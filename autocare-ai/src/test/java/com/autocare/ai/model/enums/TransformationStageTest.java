package com.autocare.ai.model.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransformationStageTest {

    @Test
    void hasExactlyFourStagesInExpectedOrder() {
        assertThat(TransformationStage.values())
                .containsExactly(
                        TransformationStage.TRANSLATE,
                        TransformationStage.REWRITE,
                        TransformationStage.EXPAND,
                        TransformationStage.ANSWER_GENERATION);
    }

    @Test
    void valueOfResolvesEachStageByName() {
        assertThat(TransformationStage.valueOf("TRANSLATE")).isEqualTo(TransformationStage.TRANSLATE);
        assertThat(TransformationStage.valueOf("REWRITE")).isEqualTo(TransformationStage.REWRITE);
        assertThat(TransformationStage.valueOf("EXPAND")).isEqualTo(TransformationStage.EXPAND);
        assertThat(TransformationStage.valueOf("ANSWER_GENERATION")).isEqualTo(TransformationStage.ANSWER_GENERATION);
    }
}
