package com.autocare.ai.service;

import com.autocare.ai.config.AutoCareProperties;
import com.autocare.ai.model.dto.KbIngestRequest;
import com.autocare.ai.model.enums.DocStatus;
import com.autocare.ai.model.enums.UserRole;
import com.autocare.ai.retrieval.KbDocumentChunkEntity;
import com.autocare.ai.retrieval.KbDocumentChunkRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import java.util.regex.Pattern;
import org.springframework.web.server.ResponseStatusException;

/**
 * Chunks and embeds ingested KB content, tags it with retrieval metadata, and manages version
 * lifecycle (activate/retire), per FR-030 and KB-AUTO-008 §4. The embedding model's output
 * dimension is verified against {@code autocare.embedding.dimension} (NFR-008) before persisting.
 */
@Service
public class KbIngestionService {

    /** Splits ingested content into paragraph-sized chunks for independent retrieval/embedding. */
    private static final Pattern PARAGRAPH_BOUNDARY = Pattern.compile("\\n\\s*\\n+");

    private final KbDocumentChunkRepository repository;
    private final EmbeddingModel embeddingModel;
    private final VectorStore vectorStore;
    private final AutoCareProperties properties;

    public KbIngestionService(
            KbDocumentChunkRepository repository,
            EmbeddingModel embeddingModel,
            VectorStore vectorStore,
            AutoCareProperties properties) {
        this.repository = repository;
        this.embeddingModel = embeddingModel;
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    /**
     * Ingests a section into an inactive candidate document version, appending its chunks to other
     * sections of that same version while preventing duplicate section content.
     */
    @Transactional
    public List<KbDocumentChunkEntity> ingest(KbIngestRequest request) {
        Assert.hasText(request.content(), "content must not be blank");
        Assert.isTrue(
                request.status() == DocStatus.RETIRED,
                "New KB versions must be ingested as RETIRED and validated before activation");
        int expectedDimension = properties.embedding().dimension();

        List<String> chunks = chunk(request.content());
        Set<UserRole> audiences = parseAudience(request.audience());
        Map<String, Object> metadata = vectorMetadata(request, audiences);
        String normalizedAudience = Arrays.stream(UserRole.values())
                .filter(audiences::contains)
                .map(Enum::name)
                .collect(Collectors.joining("|"));
        List<KbDocumentChunkEntity> existing =
                repository.findByDocCodeAndVersion(request.docCode(), request.version());
        if (existing == null) {
            throw new IllegalStateException("KB document repository returned a null version result");
        }
        if (existing.stream().anyMatch(chunk -> chunk.getStatus() != request.status()
                || !chunk.getEffectiveDate().equals(request.effectiveDate())
                || !chunk.getDocType().equals(request.docType()))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "All chunks in a document version must share status, effective date and type");
        }
        List<KbDocumentChunkEntity> saved = new ArrayList<>(chunks.size());
        List<Document> documents = new ArrayList<>(chunks.size());
        for (String chunkText : chunks) {
            boolean duplicate = existing.stream().anyMatch(chunk -> request.section().equals(chunk.getSection())
                    && normalizedAudience.equals(chunk.getAudience())
                    && chunkText.equals(chunk.getContent()));
            if (duplicate) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "That section content already exists in this document version");
            }
            float[] embedding = embeddingModel.embed(chunkText);

            KbDocumentChunkEntity entity = KbDocumentChunkEntity.builder()
                    .docCode(request.docCode())
                    .version(request.version())
                    .effectiveDate(request.effectiveDate())
                    .status(request.status())
                    .audience(normalizedAudience)
                    .vehicleFamily((String) metadata.get("vehicle_family"))
                    .section(request.section())
                    .docType(request.docType())
                    .content(chunkText)
                    .embedding(embedding)
                    .build();
            entity.requireEmbeddingDimension(expectedDimension);

            saved.add(repository.save(entity));
            documents.add(Document.builder()
                    .text(chunkText)
                    .metadata(metadata)
                    .build());
        }

        vectorStore.add(documents);
        return saved;
    }

    @Transactional
    public void changeVersionStatus(String docCode, String version, DocStatus status) {
        List<KbDocumentChunkEntity> chunks = repository.findByDocCodeAndVersion(docCode, version);
        Assert.notEmpty(chunks, "KB document version does not exist");

        FilterExpressionBuilder filters = new FilterExpressionBuilder();
        Filter.Expression versionFilter =
                filters.and(filters.eq("doc_code", docCode), filters.eq("version", version)).build();
        vectorStore.delete(versionFilter);

        for (KbDocumentChunkEntity chunk : chunks) {
            chunk.changeStatus(status);
        }
        repository.saveAll(chunks);
        vectorStore.add(chunks.stream().map(this::toDocument).toList());
    }

    private Document toDocument(KbDocumentChunkEntity chunk) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("doc_code", chunk.getDocCode());
        metadata.put("version", chunk.getVersion());
        metadata.put("effective_date", chunk.getEffectiveDate().toString());
        metadata.put("status", chunk.getStatus().name());
        metadata.put("vehicle_family", chunk.getVehicleFamily());
        metadata.put("section", chunk.getSection());
        metadata.put("doc_type", chunk.getDocType());
        for (UserRole role : UserRole.values()) {
            metadata.put("audience_" + role.name().toLowerCase(Locale.ROOT),
                    String.valueOf(chunk.audienceRoles().contains(role)));
        }
        return Document.builder().text(chunk.getContent()).metadata(metadata).build();
    }

    private Map<String, Object> vectorMetadata(KbIngestRequest request, Set<UserRole> audiences) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("doc_code", request.docCode());
        metadata.put("version", request.version());
        metadata.put("effective_date", request.effectiveDate().toString());
        metadata.put("status", request.status().name());
        metadata.put("vehicle_family", normalizeVehicleFamily(request.vehicleFamily()));
        metadata.put("section", request.section());
        metadata.put("doc_type", request.docType());

        for (UserRole role : UserRole.values()) {
            metadata.put(
                    "audience_" + role.name().toLowerCase(Locale.ROOT),
                    String.valueOf(audiences.contains(role)));
        }
        return Map.copyOf(metadata);
    }

    private Set<UserRole> parseAudience(String audience) {
        Assert.hasText(audience, "audience must not be blank");
        Set<UserRole> roles = Arrays.stream(audience.split("\\|"))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .map(role -> {
                    try {
                        return UserRole.valueOf(role.toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException exception) {
                        throw new IllegalArgumentException("Unsupported audience role: " + role, exception);
                    }
                })
                .collect(Collectors.toUnmodifiableSet());
        Assert.notEmpty(roles, "audience must contain at least one supported role");
        return roles;
    }

    private String normalizeVehicleFamily(String vehicleFamily) {
        if (vehicleFamily == null || vehicleFamily.isBlank()) {
            return "all";
        }
        String normalized = vehicleFamily.trim();
        return "all".equalsIgnoreCase(normalized) ? "all" : normalized.toUpperCase(Locale.ROOT);
    }

    private List<String> chunk(String content) {
        List<String> chunks = new ArrayList<>();
        for (String paragraph : PARAGRAPH_BOUNDARY.split(content.strip())) {
            String trimmed = paragraph.strip();
            if (!trimmed.isEmpty()) {
                chunks.add(trimmed);
            }
        }
        return chunks.isEmpty() ? List.of(content.strip()) : chunks;
    }
}
