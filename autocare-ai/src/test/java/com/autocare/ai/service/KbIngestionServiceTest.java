package com.autocare.ai.service;

import com.autocare.ai.config.AutoCareProperties;
import com.autocare.ai.model.dto.KbIngestRequest;
import com.autocare.ai.model.enums.DocStatus;
import com.autocare.ai.model.enums.UserRole;
import com.autocare.ai.retrieval.KbDocumentChunkEntity;
import com.autocare.ai.retrieval.KbDocumentChunkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KbIngestionServiceTest {

    private static final int DIMENSION = 4;

    private KbDocumentChunkRepository repository;
    private EmbeddingModel embeddingModel;
    private VectorStore vectorStore;
    private KbIngestionService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(KbDocumentChunkRepository.class);
        embeddingModel = Mockito.mock(EmbeddingModel.class);
        vectorStore = Mockito.mock(VectorStore.class);
        AutoCareProperties properties = new AutoCareProperties(
                new AutoCareProperties.Rag(0.50, false),
                new AutoCareProperties.Embedding(DIMENSION),
                new AutoCareProperties.Chat("gpt-4o-mini", "gpt-4o", 20),
                new AutoCareProperties.Kb("v1.0"),
                new AutoCareProperties.RateLimit(10, 10, 60));
        service = new KbIngestionService(repository, embeddingModel, vectorStore, properties);

        when(embeddingModel.embed(anyString())).thenReturn(new float[] {0.1f, 0.2f, 0.3f, 0.4f});
        when(repository.findByDocCodeAndVersion(anyString(), anyString())).thenReturn(List.of());
        when(repository.save(any(KbDocumentChunkEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private KbIngestRequest request(String content, DocStatus status, String version) {
        return request(content, status, version, "4");
    }

    private KbIngestRequest request(String content, DocStatus status, String version, String section) {
        return new KbIngestRequest(
                "KB-AUTO-002",
                version,
                LocalDate.of(2026, 10, 1),
                status,
                "ADVISOR",
                "AC-SD-P",
                section,
                "service-schedule",
                content,
                UserRole.ADMIN);
    }

    @Test
    void chunksContentByParagraphAndEmbedsEachChunk() {
        List<KbDocumentChunkEntity> saved =
                service.ingest(request("First paragraph.\n\nSecond paragraph.", DocStatus.RETIRED, "v1.0"));

        assertThat(saved).hasSize(2);
        assertThat(saved).extracting(KbDocumentChunkEntity::getContent)
                .containsExactly("First paragraph.", "Second paragraph.");
        verify(embeddingModel, Mockito.times(2)).embed(anyString());
        assertThat(saved).allSatisfy(chunk -> assertThat(chunk.embeddingDimension()).isEqualTo(DIMENSION));
    }

    @Test
    void tagsEveryChunkWithRequestMetadata() {
        List<KbDocumentChunkEntity> saved = service.ingest(request("Single chunk only.", DocStatus.RETIRED, "v1.0"));

        assertThat(saved).singleElement().satisfies(chunk -> {
            assertThat(chunk.getDocCode()).isEqualTo("KB-AUTO-002");
            assertThat(chunk.getVersion()).isEqualTo("v1.0");
            assertThat(chunk.getEffectiveDate()).isEqualTo(LocalDate.of(2026, 10, 1));
            assertThat(chunk.getStatus()).isEqualTo(DocStatus.RETIRED);
            assertThat(chunk.getAudience()).isEqualTo("ADVISOR");
            assertThat(chunk.getVehicleFamily()).isEqualTo("AC-SD-P");
        });
    }

    @Test
    void addsChunksToRetrievalVectorStoreWithRoleAndCitationMetadata() {
        service.ingest(new KbIngestRequest(
                "KB-AUTO-007",
                "v1.0",
                LocalDate.of(2026, 10, 1),
                DocStatus.RETIRED,
                "advisor",
                null,
                "6",
                "advisor_operations",
                "Vehicle intake checklist.",
                UserRole.ADMIN));

        verify(vectorStore).add(argThat(documents -> {
            assertThat(documents).singleElement().satisfies(document -> assertThat(document.getMetadata())
                    .containsEntry("doc_code", "KB-AUTO-007")
                    .containsEntry("version", "v1.0")
                    .containsEntry("effective_date", "2026-10-01")
                    .containsEntry("status", "RETIRED")
                    .containsEntry("vehicle_family", "all")
                    .containsEntry("section", "6")
                    .containsEntry("doc_type", "advisor_operations")
                    .containsEntry("audience_owner", "false")
                    .containsEntry("audience_advisor", "true")
                    .containsEntry("audience_admin", "false"));
            return true;
        }));
    }

    @Test
    void ingestionDoesNotActivateOrRetireOtherVersions() {
        KbDocumentChunkEntity previousActive = KbDocumentChunkEntity.builder()
                .docCode("KB-AUTO-002")
                .version("v0.9")
                .effectiveDate(LocalDate.of(2025, 10, 1))
                .status(DocStatus.ACTIVE)
                .audience("ADVISOR")
                .content("old")
                .embedding(new float[] {0f, 0f, 0f, 0f})
                .build();
        when(repository.findByDocCodeAndStatus("KB-AUTO-002", DocStatus.ACTIVE))
                .thenReturn(List.of(previousActive));

        service.ingest(request("Candidate content.", DocStatus.RETIRED, "v1.0"));

        assertThat(previousActive.getStatus()).isEqualTo(DocStatus.ACTIVE);
        assertThat(repository.findByDocCodeAndStatus("KB-AUTO-002", DocStatus.ACTIVE)).contains(previousActive);
        verify(vectorStore, Mockito.never()).delete(any(org.springframework.ai.vectorstore.filter.Filter.Expression.class));
    }

    @Test
    void rejectsBlankContent() {
        assertThatThrownBy(() -> service.ingest(request("   ", DocStatus.RETIRED, "v1.0")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failsFastOnEmbeddingDimensionMismatch() {
        when(embeddingModel.embed(anyString())).thenReturn(new float[] {0.1f, 0.2f});

        assertThatThrownBy(() -> service.ingest(request("Mismatched dimension.", DocStatus.RETIRED, "v1.0")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("embedding dimension");
    }

    @Test
    void rejectsNewVersionWithActiveStatus() {
        assertThatThrownBy(() -> service.ingest(request("Candidate content.", DocStatus.ACTIVE, "v1.1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RETIRED");
    }

    @Test
    void appendsAnotherSectionToTheSameInactiveVersion() {
        KbDocumentChunkEntity firstSection = KbDocumentChunkEntity.builder()
                .docCode("KB-AUTO-002")
                .version("v1.0")
                .effectiveDate(LocalDate.of(2026, 10, 1))
                .status(DocStatus.RETIRED)
                .audience("ADVISOR")
                .vehicleFamily("AC-SD-P")
                .section("2")
                .docType("service-schedule")
                .content("Existing section.")
                .embedding(new float[] {0.1f, 0.2f, 0.3f, 0.4f})
                .build();
        when(repository.findByDocCodeAndVersion("KB-AUTO-002", "v1.0")).thenReturn(List.of(firstSection));

        List<KbDocumentChunkEntity> added =
                service.ingest(request("Next service section.", DocStatus.RETIRED, "v1.0", "7"));

        assertThat(added).singleElement().satisfies(chunk -> {
            assertThat(chunk.getVersion()).isEqualTo("v1.0");
            assertThat(chunk.getSection()).isEqualTo("7");
            assertThat(chunk.getStatus()).isEqualTo(DocStatus.RETIRED);
        });
        verify(vectorStore).add(argThat(documents -> documents.size() == 1
                && "7".equals(documents.get(0).getMetadata().get("section"))));
    }

    @Test
    void rejectsDuplicateSectionContentWithoutReembedding() {
        KbDocumentChunkEntity existing = KbDocumentChunkEntity.builder()
                .docCode("KB-AUTO-002")
                .version("v1.0")
                .effectiveDate(LocalDate.of(2026, 10, 1))
                .status(DocStatus.RETIRED)
                .audience("ADVISOR")
                .vehicleFamily("AC-SD-P")
                .section("4")
                .docType("service-schedule")
                .content("Existing section.")
                .embedding(new float[] {0.1f, 0.2f, 0.3f, 0.4f})
                .build();
        when(repository.findByDocCodeAndVersion("KB-AUTO-002", "v1.0")).thenReturn(List.of(existing));

        assertThatThrownBy(() ->
                        service.ingest(request("Existing section.", DocStatus.RETIRED, "v1.0", "4")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("already exists");
        verify(embeddingModel, Mockito.never()).embed(anyString());
    }

    @Test
    void changesVersionStatusAndRefreshesRetrievalMetadata() {
        KbDocumentChunkEntity chunk = KbDocumentChunkEntity.builder()
                .docCode("KB-AUTO-002")
                .version("v1.0")
                .effectiveDate(LocalDate.of(2026, 10, 1))
                .status(DocStatus.RETIRED)
                .audience("ADVISOR")
                .vehicleFamily("AC-SD-P")
                .section("4")
                .docType("service-schedule")
                .content("Candidate content.")
                .embedding(new float[] {0.1f, 0.2f, 0.3f, 0.4f})
                .build();
        when(repository.findByDocCodeAndVersion("KB-AUTO-002", "v1.0")).thenReturn(List.of(chunk));

        service.changeVersionStatus("KB-AUTO-002", "v1.0", DocStatus.ACTIVE);

        assertThat(chunk.getStatus()).isEqualTo(DocStatus.ACTIVE);
        verify(repository).saveAll(List.of(chunk));
        verify(vectorStore).delete(any(org.springframework.ai.vectorstore.filter.Filter.Expression.class));
        verify(vectorStore).add(argThat(documents -> documents.size() == 1
                && "ACTIVE".equals(documents.get(0).getMetadata().get("status"))));
    }
}
