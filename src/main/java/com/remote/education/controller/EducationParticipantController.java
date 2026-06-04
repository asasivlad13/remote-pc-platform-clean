package com.remote.education.controller;

import com.remote.core.service.CurrentUserService;
import com.remote.education.dto.ControlRequest;
import com.remote.education.dto.EducationParticipantResponse;
import com.remote.education.dto.JoinEducationSessionRequest;
import com.remote.education.dto.LeaveEducationSessionRequest;
import com.remote.education.service.EducationControlService;
import com.remote.education.service.EducationParticipantService;
import com.remote.education.service.EducationScreenShareService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/education/participants")
public class EducationParticipantController {

    private final EducationParticipantService participantService;
    private final EducationControlService educationControlService;
    private final EducationScreenShareService educationScreenShareService;
    private final CurrentUserService currentUserService;

    @GetMapping("/my/{sessionCode}")
    public EducationParticipantResponse getMyStatusBySessionCode(@PathVariable String sessionCode,
                                                                 HttpServletRequest request) {
        String username = currentUserService.extractUsername(request);
        return participantService.getMyParticipantStatusBySessionCodeResponse(username, sessionCode);
    }

    @GetMapping("/status/{sessionId}")
    public EducationParticipantResponse getStatus(@PathVariable Long sessionId,
                                                  HttpServletRequest request) {
        String username = currentUserService.extractUsername(request);
        return participantService.getMyParticipantStatusResponse(username, sessionId);
    }

    @PostMapping("/join")
    public EducationParticipantResponse join(@Valid @RequestBody JoinEducationSessionRequest request,
                                             HttpServletRequest httpRequest) {
        String username = currentUserService.extractUsername(httpRequest);

        return participantService.joinSessionResponse(
                username,
                request.sessionCode(),
                request.displayName()
        );
    }

    @GetMapping("/{sessionCode}")
    public List<EducationParticipantResponse> getParticipants(@PathVariable String sessionCode,
                                                              HttpServletRequest request) {
        String username = currentUserService.extractUsername(request);
        return participantService.getParticipantResponses(username, sessionCode);
    }

    @PostMapping("/{participantId}/approve")
    public EducationParticipantResponse approve(@PathVariable Long participantId,
                                                HttpServletRequest request) {
        String username = currentUserService.extractUsername(request);
        return participantService.approveParticipantResponse(username, participantId);
    }

    @PostMapping("/{participantId}/reject")
    public EducationParticipantResponse reject(@PathVariable Long participantId,
                                               HttpServletRequest request) {
        String username = currentUserService.extractUsername(request);
        return participantService.rejectParticipantResponse(username, participantId);
    }

    @PostMapping("/control/request")
    public EducationParticipantResponse requestControl(@Valid @RequestBody ControlRequest request,
                                                       HttpServletRequest httpRequest) {
        String username = currentUserService.extractUsername(httpRequest);
        return educationControlService.requestControlResponse(username, request.sessionCode());
    }

    @PostMapping("/{participantId}/control/grant")
    public EducationParticipantResponse grantControl(@PathVariable Long participantId,
                                                     HttpServletRequest request) {
        String username = currentUserService.extractUsername(request);
        return educationControlService.grantControlResponse(username, participantId);
    }

    @PostMapping("/{participantId}/control/reject")
    public EducationParticipantResponse rejectControl(@PathVariable Long participantId,
                                                      HttpServletRequest request) {
        String username = currentUserService.extractUsername(request);
        return educationControlService.rejectControlResponse(username, participantId);
    }

    @PostMapping("/{participantId}/control/revoke")
    public EducationParticipantResponse revokeControl(@PathVariable Long participantId,
                                                      HttpServletRequest request) {
        String username = currentUserService.extractUsername(request);
        return educationControlService.revokeControlResponse(username, participantId);
    }

    @PostMapping("/screen-share/stop-my")
    public EducationParticipantResponse stopMyScreenShare(@Valid @RequestBody ControlRequest request,
                                                          HttpServletRequest httpRequest) {
        String username = currentUserService.extractUsername(httpRequest);
        return educationScreenShareService.stopMyScreenShareResponse(username, request.sessionCode());
    }

    @PostMapping("/screen-share/request")
    public EducationParticipantResponse requestScreenShare(@Valid @RequestBody ControlRequest request,
                                                           HttpServletRequest httpRequest) {
        String username = currentUserService.extractUsername(httpRequest);
        return educationScreenShareService.requestScreenShareResponse(username, request.sessionCode());
    }

    @PostMapping("/{participantId}/screen-share/grant")
    public EducationParticipantResponse grantScreenShare(@PathVariable Long participantId,
                                                         HttpServletRequest request) {
        String username = currentUserService.extractUsername(request);
        return educationScreenShareService.grantScreenShareResponse(username, participantId);
    }

    @PostMapping("/{participantId}/screen-share/reject")
    public EducationParticipantResponse rejectScreenShare(@PathVariable Long participantId,
                                                          HttpServletRequest request) {
        String username = currentUserService.extractUsername(request);
        return educationScreenShareService.rejectScreenShareResponse(username, participantId);
    }

    @PostMapping("/{participantId}/screen-share/stop")
    public EducationParticipantResponse stopScreenShare(@PathVariable Long participantId,
                                                        HttpServletRequest request) {
        String username = currentUserService.extractUsername(request);
        return educationScreenShareService.stopScreenShareResponse(username, participantId);
    }

    @PostMapping("/leave")
    public Object leaveSession(@Valid @RequestBody LeaveEducationSessionRequest request,
                               HttpServletRequest httpRequest) {
        String username = currentUserService.extractUsername(httpRequest);
        return participantService.leaveSession(username, request.sessionCode());
    }
}