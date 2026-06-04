package com.remote.education.controller;

import com.remote.education.service.EducationChatService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/education/chat")
public class EducationChatController {

    private final EducationChatService educationChatService;

    @GetMapping("/{sessionCode}")
    public ResponseEntity<?> getMessages(@PathVariable String sessionCode) {
        return ResponseEntity.ok(educationChatService.getMessages(sessionCode));
    }

    @PostMapping("/{sessionCode}")
    public ResponseEntity<?> sendMessage(@PathVariable String sessionCode,
                                         @Valid @RequestBody SendChatMessageRequest request) {
        return ResponseEntity.ok(
                educationChatService.sendMessage(
                        sessionCode,
                        request.message(),
                        request.recipientId()
                )
        );
    }

    public record SendChatMessageRequest(
            @NotBlank
            @Size(max = 2000)
            String message,

            Long recipientId
    ) {
    }
}