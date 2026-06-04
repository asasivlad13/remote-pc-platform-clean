package com.remote.support.controller;

import com.remote.core.service.CurrentUserService;
import com.remote.support.dto.SupportChatMessageRequest;
import com.remote.support.dto.SupportChatMessageResponse;
import com.remote.support.service.SupportChatService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/support/sessions/{sessionCode}/chat")
public class SupportChatController {

    private final SupportChatService supportChatService;
    private final CurrentUserService currentUserService;

    @GetMapping
    public List<SupportChatMessageResponse> getMessages(@PathVariable String sessionCode,
                                                        HttpServletRequest request) {
        String username = currentUserService.extractUsername(request);
        return supportChatService.getMessages(username, sessionCode);
    }

    @PostMapping
    public SupportChatMessageResponse sendMessage(@PathVariable String sessionCode,
                                                  @Valid @RequestBody SupportChatMessageRequest requestBody,
                                                  HttpServletRequest request) {
        String username = currentUserService.extractUsername(request);

        return supportChatService.sendMessage(
                username,
                sessionCode,
                requestBody.message()
        );
    }
}