package com.autocare.ai.model.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CitationStatusTest {

    @Test
    void hasExactlyThreeStatusesInExpectedOrder() {
        assertThat(CitationStatus.values())
                .containsExactly(CitationStatus.VALID, CitationStatus.BLOCKED, CitationStatus.NOT_APPLICABLE);
    }

    @Test
    void valueOfResolvesEachStatusByName() {
        assertThat(CitationStatus.valueOf("VALID")).isEqualTo(CitationStatus.VALID);
        assertThat(CitationStatus.valueOf("BLOCKED")).isEqualTo(CitationStatus.BLOCKED);
        assertThat(CitationStatus.valueOf("NOT_APPLICABLE")).isEqualTo(CitationStatus.NOT_APPLICABLE);
    }
}
