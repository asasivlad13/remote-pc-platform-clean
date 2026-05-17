package com.remote.controller;

import com.remote.service.EducationChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/education/chat")
public class EducationChatController {

    private final EducationChatService educationChatService;

    public EducationChatController(EducationChatService educationChatService) {
        this.educationChatService = educationChatService;
    }

    @GetMapping("/{sessionCode}")
    public ResponseEntity<?> getMessages(@PathVariable String sessionCode) {
        return ResponseEntity.ok(educationChatService.getMessages(sessionCode));
    }

    @PostMapping("/{sessionCode}")
    public ResponseEntity<?> sendMessage(@PathVariable String sessionCode,
                                         @RequestBody SendChatMessageRequest request) {
        return ResponseEntity.ok(
                educationChatService.sendMessage(
                        sessionCode,
                        request.message(),
                        request.recipientId()
                )
        );
    }

    public record SendChatMessageRequest(
            String message,
            Long recipientId
    ) {
    }
}