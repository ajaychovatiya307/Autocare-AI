package com.autocare.ai.model.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.autocare.ai.model.enums.CitationStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatResponseTest {

    @Test
    void exposesAllAccessorsForASuccessfulGroundedAnswer() {
        Instant timestamp = Instant.parse("2026-10-26T10:15:30Z");
        SourceCitation citation = new SourceCitation("KB-AUTO-007", "v1.0", "4");

        ChatResponse response = new ChatResponse(
                "b6b6b6c2-8a2b-4e9b-9a3d-6b8c2f6e1a10",
                "The SLA for warranty evidence completeness review is within 1 business day.",
                List.of(citation),
                List.of("TRANSLATE:none", "REWRITE:none", "EXPAND:none"),
                List.of("KB-AUTO-007"),
                false,
                true,
                CitationStatus.VALID,
                "AnswerChatClient",
                "What is the SLA for warranty evidence review?",
                "What is the SLA for warranty evidence review?",
                "7f0a1c3e-2b4d-4a6f-9c3a-1d2e3f4a5b6c",
                timestamp);

        assertThat(response.sessionId()).isEqualTo("b6b6b6c2-8a2b-4e9b-9a3d-6b8c2f6e1a10");
        assertThat(response.citations()).containsExactly(citation);
        assertThat(response.transformationsApplied()).containsExactly("TRANSLATE:none", "REWRITE:none", "EXPAND:none");
        assertThat(response.retrievedDocCodes()).containsExactly("KB-AUTO-007");
        assertThat(response.escalated()).isFalse();
        assertThat(response.citationValidated()).isTrue();
        assertThat(response.citationStatus()).isEqualTo(CitationStatus.VALID);
        assertThat(response.chatClient()).isEqualTo("AnswerChatClient");
        assertThat(response.originalQuery()).isEqualTo("What is the SLA for warranty evidence review?");
        assertThat(response.transformedQuery()).isEqualTo("What is the SLA for warranty evidence review?");
        assertThat(response.correlationId()).isEqualTo("7f0a1c3e-2b4d-4a6f-9c3a-1d2e3f4a5b6c");
        assertThat(response.timestamp()).isEqualTo(timestamp);
    }

    @Test
    void representsControlledEscalationWithEmptyContext() {
        ChatResponse response = new ChatResponse(
                "b6b6b6c2-8a2b-4e9b-9a3d-6b8c2f6e1a10",
                "No matching vehicle service or warranty information was found in the AutoCare Knowledge Base.",
                List.of(),
                List.of("TRANSLATE:none", "REWRITE:none", "EXPAND:ran"),
                List.of(),
                true,
                false,
                CitationStatus.NOT_APPLICABLE,
                "AnswerChatClient",
                "original raw query",
                "expanded standalone query",
                "7f0a1c3e-2b4d-4a6f-9c3a-1d2e3f4a5b6c",
                Instant.parse("2026-10-26T10:16:05Z"));

        assertThat(response.citations()).isEmpty();
        assertThat(response.retrievedDocCodes()).isEmpty();
        assertThat(response.escalated()).isTrue();
        assertThat(response.citationValidated()).isFalse();
        assertThat(response.citationStatus()).isEqualTo(CitationStatus.NOT_APPLICABLE);
    }
}
