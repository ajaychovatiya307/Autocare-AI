package com.autocare.ai.model.enums;

/**
 * Query transformation pipeline stages applied before answer generation, per SRS §4.4/§4.7.
 */
public enum TransformationStage {
    TRANSLATE,
    REWRITE,
    EXPAND,
    ANSWER_GENERATION
}
