package com.autocare.ai.controller;

import com.autocare.ai.config.SecurityConfig;
import com.autocare.ai.model.dto.ChatResponse;
import com.autocare.ai.model.dto.SourceCitation;
import com.autocare.ai.model.enums.CitationStatus;
import com.autocare.ai.model.enums.UserRole;
import com.autocare.ai.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
@Import(SecurityConfig.class)
class ChatControllerSyncTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ChatService chatService;

    private Map<String, Object> payload(UserRole role) {
        Map<String, Object> body = new HashMap<>();
        body.put("sessionId", "b6b6b6c2-8a2b-4e9b-9a3d-6b8c2f6e1a10");
        body.put("role", role.name());
        body.put("vehicleFamily", "AC-SD-P");
        body.put("query", "What is due at 20,000 km?");
        body.put("language", "en");
        return body;
    }

    private ChatResponse groundedResponse() {
        return new ChatResponse(
                "b6b6b6c2-8a2b-4e9b-9a3d-6b8c2f6e1a10",
                "Standard Service B is due at 20,000 km.",
                List.of(new SourceCitation("KB-AUTO-002", "v1.0", "2")),
                List.of("REWRITE"),
                List.of("KB-AUTO-002"),
                false,
                true,
                CitationStatus.VALID,
                ChatService.ANSWER_CHAT_CLIENT,
                "What is due at 20,000 km?",
                "service due at 20000 km",
                "c0ffee00-0000-4000-8000-000000000000",
                Instant.parse("2026-10-26T10:00:00Z"));
    }

    @Test
    @WithMockUser(roles = "ADVISOR")
    void returns200WithChatResponseSchemaForMatchingRole() throws Exception {
        when(chatService.chat(any())).thenReturn(groundedResponse());

        mockMvc.perform(post("/ai/autocare/chat/sync")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(payload(UserRole.ADVISOR))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("b6b6b6c2-8a2b-4e9b-9a3d-6b8c2f6e1a10"))
                .andExpect(jsonPath("$.answer").value("Standard Service B is due at 20,000 km."))
                .andExpect(jsonPath("$.citations[0].docCode").value("KB-AUTO-002"))
                .andExpect(jsonPath("$.retrievedDocCodes[0]").value("KB-AUTO-002"))
                .andExpect(jsonPath("$.citationStatus").value("VALID"))
                .andExpect(jsonPath("$.citationValidated").value(true))
                .andExpect(jsonPath("$.escalated").value(false))
                .andExpect(jsonPath("$.chatClient").value(ChatService.ANSWER_CHAT_CLIENT))
                .andExpect(jsonPath("$.originalQuery").value("What is due at 20,000 km?"))
                .andExpect(jsonPath("$.transformedQuery").value("service due at 20000 km"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void returns403WhenBodyRoleDoesNotMatchAuthenticatedRole() throws Exception {
        mockMvc.perform(post("/ai/autocare/chat/sync")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(payload(UserRole.ADVISOR))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Access is forbidden."));
    }

    @Test
    @WithMockUser(roles = "ADVISOR")
    void returns400WhenRequiredFieldsAreMissing() throws Exception {
        Map<String, Object> invalid = payload(UserRole.ADVISOR);
        invalid.put("query", "");

        mockMvc.perform(post("/ai/autocare/chat/sync")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(post("/ai/autocare/chat/sync")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(payload(UserRole.ADVISOR))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Authentication is required."))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/ai/autocare/chat/sync"));
    }

}
