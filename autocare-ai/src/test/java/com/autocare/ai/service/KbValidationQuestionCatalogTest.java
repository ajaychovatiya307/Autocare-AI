package com.autocare.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.autocare.ai.model.enums.UserRole;
import org.junit.jupiter.api.Test;

class KbValidationQuestionCatalogTest {

    private final KbValidationQuestionCatalog catalog = new KbValidationQuestionCatalog();

    @Test
    void loadsAllQuestionsFromKbMetadataSectionSix() {
        assertThat(catalog.forDocument("KB-AUTO-001"))
                .hasSize(2)
                .first()
                .satisfies(question -> {
                    assertThat(question.question()).isEqualTo("What vehicles do you support?");
                    assertThat(question.expectedSections()).containsExactly("2");
                    assertThat(question.role()).isEqualTo(UserRole.OWNER);
                });
        assertThat(catalog.forDocument("KB-AUTO-007"))
                .hasSize(2)
                .satisfiesExactly(
                        intake -> {
                            assertThat(intake.role()).isEqualTo(UserRole.ADVISOR);
                            assertThat(intake.expectedSections()).containsExactly("2", "6");
                        },
                        audienceCheck -> {
                            assertThat(audienceCheck.role()).isEqualTo(UserRole.OWNER);
                            assertThat(audienceCheck.audienceCheck()).isTrue();
                        });
        assertThat(catalog.forDocument("KB-AUTO-008"))
                .extracting(KbValidationQuestionCatalog.ValidationQuestion::number)
                .containsExactly(1, 2);
    }

    @Test
    void includesEveryDocumentInTheSrsQuestionCatalog() {
        for (int document = 1; document <= 8; document++) {
            assertThat(catalog.forDocument("KB-AUTO-%03d".formatted(document))).hasSize(2);
        }
    }
}
