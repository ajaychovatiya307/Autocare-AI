package com.autocare.ai.controller;

import com.autocare.ai.config.SecurityConfig;
import com.autocare.ai.exception.NoMatchingContextException;
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
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
@Import(SecurityConfig.class)
class ChatControllerStreamTest {

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

    private ChatResponse response(String answer, boolean escalated, CitationStatus citationStatus) {
        return new ChatResponse(
                "b6b6b6c2-8a2b-4e9b-9a3d-6b8c2f6e1a10",
                answer,
                escalated ? List.of() : List.of(new SourceCitation("KB-AUTO-002", "v1.0", "2")),
                List.of(),
                escalated ? List.of() : List.of("KB-AUTO-002"),
                escalated,
                citationStatus == CitationStatus.VALID,
                citationStatus,
                ChatService.ANSWER_CHAT_CLIENT,
                "What is due at 20,000 km?",
                "What is due at 20,000 km?",
                "c0ffee00-0000-4000-8000-000000000000",
                Instant.parse("2026-10-26T10:00:00Z"));
    }

    private String performStream(UserRole bodyRole) throws Exception {
        MvcResult started = mockMvc.perform(post("/ai/autocare/chat/stream")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(payload(bodyRole))))
                .andExpect(request().asyncStarted())
                .andReturn();

        return mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    @WithMockUser(roles = "ADVISOR")
    void streamsValidatedChunksForMatchingRole() throws Exception {
        when(chatService.streamChat(any()))
                .thenReturn(Flux.just(response("Standard Service B is due at 20,000 km.", false, CitationStatus.VALID)));

        String body = performStream(UserRole.ADVISOR);

        assertThat(body).contains("Standard Service B is due at 20,000 km.").contains("\"citationStatus\":\"VALID\"");
    }

    @Test
    @WithMockUser(roles = "ADVISOR")
    void streamEmitsBlockedEscalationInsteadOfUnsupportedAnswer() throws Exception {
        when(chatService.streamChat(any()))
                .thenReturn(Flux.just(
                        response(NoMatchingContextException.ESCALATION_MESSAGE, true, CitationStatus.BLOCKED)));

        String body = performStream(UserRole.ADVISOR);

        assertThat(body)
                .contains(NoMatchingContextException.ESCALATION_MESSAGE)
                .contains("\"escalated\":true")
                .contains("\"citationStatus\":\"BLOCKED\"");
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void returns403WhenBodyRoleDoesNotMatchAuthenticatedRole() throws Exception {
        mockMvc.perform(post("/ai/autocare/chat/stream")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(payload(UserRole.ADVISOR))))
                .andExpect(status().isForbidden());
    }

    @Test
    void returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(post("/ai/autocare/chat/stream")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(payload(UserRole.ADVISOR))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication is required."));
    }

    @Test
    @WithMockUser(roles = "ADVISOR")
    void emitsStructuredErrorEventWhenStreamingFails() throws Exception {
        when(chatService.streamChat(any())).thenReturn(Flux.error(new NoMatchingContextException()));
        MvcResult started = mockMvc.perform(post("/ai/autocare/chat/stream")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(payload(UserRole.ADVISOR))))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(body)
                .contains("event:error")
                .contains("\"status\":404")
                .contains(NoMatchingContextException.ESCALATION_MESSAGE);
    }
}
