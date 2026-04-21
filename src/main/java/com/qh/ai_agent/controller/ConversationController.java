package com.qh.ai_agent.controller;

import com.qh.ai_agent.entity.Conversation;
import com.qh.ai_agent.entity.ConversationMessage;
import com.qh.ai_agent.entity.User;
import com.qh.ai_agent.service.ConversationService;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping
    public ResponseEntity<?> list(@AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).build();
        List<Conversation> conversations = conversationService.listByUserId(user.getId());
        return ResponseEntity.ok(conversations);
    }

    @PostMapping
    public ResponseEntity<?> create(@AuthenticationPrincipal User user,
                                    @RequestBody CreateRequest request) {
        if (user == null) return ResponseEntity.status(401).build();
        Conversation conv = conversationService.create(
                request.getId(), user.getId(), request.getTitle(), request.getAgentType());
        return ResponseEntity.ok(conv);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable String id, @AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).build();
        Conversation conv = conversationService.getById(id);
        if (conv == null) return ResponseEntity.notFound().build();
        List<ConversationMessage> messages = conversationService.getMessages(id);
        return ResponseEntity.ok(Map.of("conversation", conv, "messages", messages));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable String id,
                                    @RequestBody UpdateRequest request,
                                    @AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).build();
        conversationService.updateTitle(id, request.getTitle());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String id, @AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).build();
        conversationService.delete(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<?> addMessage(@PathVariable String id,
                                        @RequestBody MessageRequest request,
                                        @AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).build();
        conversationService.addMessage(id, request.getRole(), request.getContent());
        return ResponseEntity.ok().build();
    }

    @Data
    public static class CreateRequest {
        private String id;
        private String title;
        private String agentType;
    }

    @Data
    public static class UpdateRequest {
        private String title;
    }

    @Data
    public static class MessageRequest {
        private String role;
        private String content;
    }
}
