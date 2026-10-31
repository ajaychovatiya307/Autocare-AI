package com.autocare.ai.audit;

import com.autocare.ai.model.dto.SourceCitation;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;
import org.springframework.util.StringUtils;

/**
 * Serializes the {@code retrieved_chunk_refs} column — the {@code {docCode, version, section}} evidence
 * actually supplied to the LLM — to/from a JSON text column, per architecture.md §16.
 */
@Converter
class JsonSourceCitationListConverter implements AttributeConverter<List<SourceCitation>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<SourceCitation>> LIST_TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<SourceCitation> attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize retrieved chunk refs to JSON", e);
        }
    }

    @Override
    public List<SourceCitation> convertToEntityAttribute(String dbData) {
        if (!StringUtils.hasText(dbData)) {
            return List.of();
        }
        try {
            return MAPPER.readValue(dbData, LIST_TYPE);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to deserialize retrieved chunk refs from JSON", e);
        }
    }
}
