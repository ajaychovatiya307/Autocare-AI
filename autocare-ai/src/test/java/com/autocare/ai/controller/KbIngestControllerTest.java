package com.autocare.ai.controller;

import com.autocare.ai.config.SecurityConfig;
import com.autocare.ai.model.enums.DocStatus;
import com.autocare.ai.model.enums.UserRole;
import com.autocare.ai.retrieval.KbDocumentChunkEntity;
import com.autocare.ai.service.KbVersionManagementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(KbIngestController.class)
@Import(SecurityConfig.class)
class KbIngestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private KbVersionManagementService versionManagementService;

    private Map<String, Object> payload(UserRole requesterRole) {
        return payload(requesterRole, DocStatus.RETIRED);
    }

    private Map<String, Object> payload(UserRole requesterRole, DocStatus status) {
        return Map.of(
                "docCode", "KB-AUTO-002",
                "version", "v1.0",
                "effectiveDate", "2026-10-01",
                "status", status.name(),
                "audience", "ADVISOR",
                "vehicleFamily", "AC-SD-P",
                "section", "4",
                "docType", "service-schedule",
                "content", "Standard service B is due at 20,000 km.",
                "requesterRole", requesterRole.name());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminWithMatchingRequesterRoleIngestsSuccessfully() throws Exception {
        when(versionManagementService.ingest(any()))
                .thenReturn(new KbVersionManagementService.IngestionSummary(
                        "KB-AUTO-002", "v1.0", "RETIRED", 1));

        mockMvc.perform(post("/kb/ingest")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(payload(UserRole.ADMIN))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.docCode").value("KB-AUTO-002"))
                .andExpect(jsonPath("$.chunkCount").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void requesterRoleMismatchIsForbiddenEvenForAnAuthenticatedAdmin() throws Exception {
        mockMvc.perform(post("/kb/ingest")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(payload(UserRole.ADVISOR))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void nonAdminRoleIsForbiddenBySecurityConfig() throws Exception {
        mockMvc.perform(post("/kb/ingest")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(payload(UserRole.OWNER))))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedRequestIsUnauthorized() throws Exception {
        mockMvc.perform(post("/kb/ingest")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(payload(UserRole.ADMIN))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void activeVersionCannotBeIngestedBeforeValidation() throws Exception {
        mockMvc.perform(post("/kb/ingest")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(payload(UserRole.ADMIN, DocStatus.ACTIVE))))
                .andExpect(status().isBadRequest());
    }
}
