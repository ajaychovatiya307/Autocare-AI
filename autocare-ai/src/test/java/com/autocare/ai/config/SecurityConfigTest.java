package com.autocare.ai.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SecurityTestController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "OWNER")
    void ownerCanAccessChatButCannotIngestKnowledgeBase() throws Exception {
        mockMvc.perform(get("/ai/autocare/chat/sync"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/kb/ingest"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADVISOR")
    void advisorCanAccessChat() throws Exception {
        mockMvc.perform(get("/ai/autocare/chat/sync"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanAccessChatAndIngestKnowledgeBase() throws Exception {
        mockMvc.perform(get("/ai/autocare/chat/sync"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/kb/ingest"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/ai/autocare/monitoring/test"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void ownerCannotAccessMonitoring() throws Exception {
        mockMvc.perform(get("/ai/autocare/monitoring/test"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADVISOR")
    void advisorCannotAccessMonitoring() throws Exception {
        mockMvc.perform(get("/ai/autocare/monitoring/test"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedUsersCannotAccessProtectedEndpoints() throws Exception {
        mockMvc.perform(get("/ai/autocare/chat/sync"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/ai/autocare/monitoring/test"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/kb/ingest"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void authenticatedUserWithoutAnApplicationRoleCannotAccessProtectedEndpoints() throws Exception {
        mockMvc.perform(get("/ai/autocare/chat/sync"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/kb/ingest"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "eleventh-chat-user", roles = "OWNER")
    void eleventhChatRequestGets429AndRetryAfter() throws Exception {
        for (int request = 1; request <= 10; request++) {
            mockMvc.perform(get("/ai/autocare/chat/sync"));
        }

        mockMvc.perform(get("/ai/autocare/chat/sync"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").value("Rate limit exceeded. Please retry later."));
    }

    @Test
    @WithMockUser(username = "eleventh-kb-user", roles = "ADMIN")
    void kbRateLimitIsIndependentFromChatLimit() throws Exception {
        for (int request = 1; request <= 10; request++) {
            mockMvc.perform(get("/kb/ingest"));
        }
        mockMvc.perform(get("/kb/ingest"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    @WithMockUser(username = "log-file-user", roles = "OWNER")
    void requestMetadataIsWrittenToTheRollingLogFile() throws Exception {
        mockMvc.perform(get("/ai/autocare/chat/sync"));
        Path logFile = Path.of("logs", "autocare-ai.log");
        assertThat(Files.exists(logFile)).isTrue();
        String fileContent = Files.readString(logFile);
        assertThat(fileContent)
                .contains("endpoint=/ai/autocare/chat/sync")
                .contains("mode=sync")
                .contains("advisors=")
                .contains("CitationValidationAdvisor");
        String requestLog = fileContent.lines()
                .filter(line -> line.contains("user=log-file-user") && line.contains("endpoint=/ai/autocare/chat/sync"))
                .reduce((first, second) -> second)
                .orElseThrow();
        assertThat(requestLog).doesNotContain("StreamingCitationValidationAdvisor");
    }

}

@RestController
class SecurityTestController {

    @GetMapping("/ai/autocare/chat/sync")
    String chat() {
        return "chat";
    }

    @GetMapping("/kb/ingest")
    String ingest() {
        return "ingest";
    }

    @GetMapping("/ai/autocare/monitoring/{correlationId}")
    String monitoring() {
        return "monitoring";
    }
}
