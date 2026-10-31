package com.autocare.ai.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autocare.ai.config.SecurityConfig;
import com.autocare.ai.service.KbVersionManagementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(KbManagementController.class)
@Import(SecurityConfig.class)
class KbManagementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KbVersionManagementService versionService;

    @Test
    @WithMockUser(roles = "OWNER")
    void nonAdminCannotListDocumentsOrManageVersions() throws Exception {
        mockMvc.perform(get("/kb/documents")).andExpect(status().isForbidden());
        mockMvc.perform(post("/kb/documents/KB-AUTO-001/versions/v1.1/validate"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/kb/documents/KB-AUTO-001/versions/v1.1/activate"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/kb/documents/KB-AUTO-001/rollback")).andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCannotListDocumentsOrManageVersions() throws Exception {
        mockMvc.perform(get("/kb/documents")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/kb/documents/KB-AUTO-001/versions/v1.1/validate"))
                .andExpect(status().isUnauthorized());
    }

}
