package com.autocare.ai.retrieval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.util.StringUtils;

/**
 * Serializes the chunk embedding ({@code float[]}) to/from a JSON text column, mirroring the
 * {@code JsonStringListConverter} / {@code JsonSourceCitationListConverter} pattern used in the audit
 * entity. MariaDB-native vector storage is handled separately by {@code MariaDBVectorStore}
 * (see {@code VectorStoreConfig}); this JPA entity/converter exists for metadata-driven querying
 * via {@code KbDocumentChunkRepository} (T5.2).
 */
@Converter
class JsonFloatArrayConverter implements AttributeConverter<float[], String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<float[]> ARRAY_TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(float[] attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize chunk embedding to JSON", e);
        }
    }

    @Override
    public float[] convertToEntityAttribute(String dbData) {
        if (!StringUtils.hasText(dbData)) {
            return new float[0];
        }
        try {
            return MAPPER.readValue(dbData, ARRAY_TYPE);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to deserialize chunk embedding from JSON", e);
        }
    }
}
