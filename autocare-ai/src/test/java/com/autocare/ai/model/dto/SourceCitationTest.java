package com.autocare.ai.model.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SourceCitationTest {

    @Test
    void exposesDocCodeVersionAndSectionAccessors() {
        SourceCitation citation = new SourceCitation("KB-AUTO-007", "v1.0", "4");

        assertThat(citation.docCode()).isEqualTo("KB-AUTO-007");
        assertThat(citation.version()).isEqualTo("v1.0");
        assertThat(citation.section()).isEqualTo("4");
    }

    @Test
    void equalsAndHashCodeAreValueBased() {
        SourceCitation a = new SourceCitation("KB-AUTO-007", "v1.0", "4");
        SourceCitation b = new SourceCitation("KB-AUTO-007", "v1.0", "4");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
