package com.remote.controller;

import com.remote.service.SupportChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/support/sessions/{sessionCode}/chat")
public class SupportChatController {

    private final SupportChatService supportChatService;

    public SupportChatController(SupportChatService supportChatService) {
        this.supportChatService = supportChatService;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getMessages(@PathVariable String sessionCode) {
        return ResponseEntity.ok(supportChatService.getMessages(getCurrentUsername(), sessionCode));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> sendMessage(@PathVariable String sessionCode,
                                                           @RequestBody Map<String, Object> request) {
        String message = request.get("message") != null ? request.get("message").toString() : null;
        return ResponseEntity.ok(supportChatService.sendMessage(getCurrentUsername(), sessionCode, message));
    }

    private String getCurrentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
