package com.autocare.ai.controller;

import com.autocare.ai.service.KbVersionManagementService;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/kb")
public class KbManagementController {

    private final KbVersionManagementService versionService;

    public KbManagementController(KbVersionManagementService versionService) {
        this.versionService = versionService;
    }

    @GetMapping("/documents")
    public List<KbVersionManagementService.DocumentSummary> listDocuments() {
        return versionService.listDocuments();
    }

    @PostMapping("/documents/{docCode}/versions/{version}/validate")
    public KbVersionManagementService.ValidationSummary validate(
            @PathVariable String docCode, @PathVariable String version, Authentication authentication) {
        return versionService.validate(docCode, version, authentication.getName());
    }

    @PostMapping("/documents/{docCode}/versions/{version}/activate")
    public KbVersionManagementService.ActivationSummary activate(
            @PathVariable String docCode, @PathVariable String version, Authentication authentication) {
        return versionService.activate(docCode, version, authentication.getName());
    }

    @PostMapping("/documents/{docCode}/rollback")
    public KbVersionManagementService.ActivationSummary rollback(
            @PathVariable String docCode, Authentication authentication) {
        return versionService.rollback(docCode, authentication.getName());
    }
}
