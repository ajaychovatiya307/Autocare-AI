package com.autocare.ai.controller;

import com.autocare.ai.model.dto.ChatRequest;
import com.autocare.ai.model.dto.ChatResponse;
import com.autocare.ai.model.dto.ErrorResponse;
import com.autocare.ai.exception.GlobalExceptionHandler;
import com.autocare.ai.model.enums.UserRole;
import com.autocare.ai.service.ChatService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

/**
 * Chat API (architecture.md §8, SRS §6). The client-supplied {@link ChatRequest#role()} is never
 * trusted alone: it is cross-checked against the authenticated principal's granted authority and the
 * request is rejected with {@code 403} on mismatch (architecture.md §15.1).
 */
@RestController
@RequestMapping("/ai/autocare/chat")
public class ChatController {

    private final ChatService chatService;
    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/sync")
    public ResponseEntity<ChatResponse> chatSync(
            @Valid @RequestBody ChatRequest request, Authentication authentication) {
        requireMatchingRole(request.role(), authentication);
        return ResponseEntity.ok(chatService.chat(request));
    }

    /**
     * Streams the answer only after {@code StreamingCitationValidationAdvisor} (T9.5) has validated the
     * fully buffered response, so no unvalidated partial answer is ever emitted (NFR-001 for streaming).
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> chatStream(
            @Valid @RequestBody ChatRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        requireMatchingRole(request.role(), authentication);
        return chatService.streamChat(request)
                .map(response -> ServerSentEvent.builder((Object) response).event("message").build())
                .onErrorResume(exception -> {
                    ErrorResponse error = GlobalExceptionHandler.streamError(exception, servletRequest);
                    return Flux.just(ServerSentEvent.builder((Object) error).event("error").build());
                });
    }

    private void requireMatchingRole(UserRole requestedRole, Authentication authentication) {
        String expectedAuthority = "ROLE_" + requestedRole;
        boolean matches = authentication != null
                && authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch(expectedAuthority::equals);
        if (!matches) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "role does not match the authenticated principal");
        }
    }
}
