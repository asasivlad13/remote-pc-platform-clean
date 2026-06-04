package com.remote.education.controller;

import com.remote.core.service.CurrentUserService;
import com.remote.education.dto.ActiveScreenShareResponse;
import com.remote.education.dto.EducationAgentResponse;
import com.remote.education.service.EducationScreenShareService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/education/screen-share")
public class EducationScreenShareController {

    private final EducationScreenShareService educationScreenShareService;
    private final CurrentUserService currentUserService;

    @GetMapping("/my-agent")
    public EducationAgentResponse getMyAgent(HttpServletRequest request) {
        String username = currentUserService.extractUsername(request);
        return educationScreenShareService.getMyAgent(username);
    }

    @GetMapping("/participant/{participantId}")
    public EducationAgentResponse getParticipantAgent(@PathVariable Long participantId,
                                                      HttpServletRequest request) {
        String username = currentUserService.extractUsername(request);
        return educationScreenShareService.getParticipantAgent(participantId, username);
    }

    @GetMapping("/active/{sessionCode}")
    public ActiveScreenShareResponse getActiveScreenShare(@PathVariable String sessionCode,
                                                          HttpServletRequest request) {
        String username = currentUserService.extractUsername(request);
        return educationScreenShareService.getActiveScreenShare(sessionCode, username);
    }
}