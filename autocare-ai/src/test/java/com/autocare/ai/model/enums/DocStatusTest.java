package com.autocare.ai.model.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocStatusTest {

    @Test
    void hasExactlyThreeStatusesInExpectedOrder() {
        assertThat(DocStatus.values()).containsExactly(DocStatus.ACTIVE, DocStatus.SUPERSEDED, DocStatus.RETIRED);
    }

    @Test
    void valueOfResolvesEachStatusByName() {
        assertThat(DocStatus.valueOf("ACTIVE")).isEqualTo(DocStatus.ACTIVE);
        assertThat(DocStatus.valueOf("SUPERSEDED")).isEqualTo(DocStatus.SUPERSEDED);
        assertThat(DocStatus.valueOf("RETIRED")).isEqualTo(DocStatus.RETIRED);
    }
}
