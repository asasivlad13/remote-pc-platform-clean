package com.remote.controller;

import com.remote.config.JwtUtil;
import com.remote.model.EducationSessionParticipant;
import com.remote.service.EducationParticipantService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/education/participants")
public class EducationParticipantController {

    private final EducationParticipantService participantService;
    private final JwtUtil jwtUtil;

    public EducationParticipantController(EducationParticipantService participantService,
                                          JwtUtil jwtUtil) {
        this.participantService = participantService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("/my/{sessionCode}")
    public ParticipantResponse getMyStatusBySessionCode(@PathVariable String sessionCode,
                                                        HttpServletRequest request) {
        String username = extractUsername(request);
        return toResponse(participantService.getMyParticipantStatusBySessionCode(username, sessionCode));
    }

    @GetMapping("/status/{sessionId}")
    public ParticipantResponse getStatus(@PathVariable Long sessionId,
                                         HttpServletRequest request) {
        String username = extractUsername(request);
        return toResponse(participantService.getMyParticipantStatus(username, sessionId));
    }

    @PostMapping("/join")
    public ParticipantResponse join(@RequestBody JoinEducationSessionRequest request,
                                    HttpServletRequest httpRequest) {
        String username = extractUsername(httpRequest);

        EducationSessionParticipant participant = participantService.joinSession(
                username,
                request.sessionCode(),
                request.displayName()
        );

        return toResponse(participant);
    }

    @GetMapping("/{sessionCode}")
    public List<ParticipantResponse> getParticipants(@PathVariable String sessionCode,
                                                     HttpServletRequest request) {
        String username = extractUsername(request);

        return participantService.getParticipants(username, sessionCode)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping("/{participantId}/approve")
    public ParticipantResponse approve(@PathVariable Long participantId,
                                       HttpServletRequest request) {
        String username = extractUsername(request);
        return toResponse(participantService.approveParticipant(username, participantId));
    }

    @PostMapping("/{participantId}/reject")
    public ParticipantResponse reject(@PathVariable Long participantId,
                                      HttpServletRequest request) {
        String username = extractUsername(request);
        return toResponse(participantService.rejectParticipant(username, participantId));
    }

    @PostMapping("/control/request")
    public ParticipantResponse requestControl(@RequestBody ControlRequest request,
                                              HttpServletRequest httpRequest) {
        String username = extractUsername(httpRequest);
        return toResponse(participantService.requestControl(username, request.sessionCode()));
    }

    @PostMapping("/{participantId}/control/grant")
    public ParticipantResponse grantControl(@PathVariable Long participantId,
                                            HttpServletRequest request) {
        String username = extractUsername(request);
        return toResponse(participantService.grantControl(username, participantId));
    }

    @PostMapping("/{participantId}/control/reject")
    public ParticipantResponse rejectControl(@PathVariable Long participantId,
                                             HttpServletRequest request) {
        String username = extractUsername(request);
        return toResponse(participantService.rejectControl(username, participantId));
    }

    @PostMapping("/{participantId}/control/revoke")
    public ParticipantResponse revokeControl(@PathVariable Long participantId,
                                             HttpServletRequest request) {
        String username = extractUsername(request);
        return toResponse(participantService.revokeControl(username, participantId));
    }


    @PostMapping("/screen-share/stop-my")
    public ParticipantResponse stopMyScreenShare(@RequestBody ControlRequest request,
                                                 HttpServletRequest httpRequest) {
        String username = extractUsername(httpRequest);
        return toResponse(participantService.stopMyScreenShare(username, request.sessionCode()));
    }

    @PostMapping("/screen-share/request")
    public ParticipantResponse requestScreenShare(@RequestBody ControlRequest request,
                                                  HttpServletRequest httpRequest) {
        String username = extractUsername(httpRequest);
        return toResponse(participantService.requestScreenShare(username, request.sessionCode()));
    }

    @PostMapping("/{participantId}/screen-share/grant")
    public ParticipantResponse grantScreenShare(@PathVariable Long participantId,
                                                HttpServletRequest request) {
        String username = extractUsername(request);
        return toResponse(participantService.grantScreenShare(username, participantId));
    }

    @PostMapping("/{participantId}/screen-share/reject")
    public ParticipantResponse rejectScreenShare(@PathVariable Long participantId,
                                                 HttpServletRequest request) {
        String username = extractUsername(request);
        return toResponse(participantService.rejectScreenShare(username, participantId));
    }

    @PostMapping("/{participantId}/screen-share/stop")
    public ParticipantResponse stopScreenShare(@PathVariable Long participantId,
                                               HttpServletRequest request) {
        String username = extractUsername(request);
        return toResponse(participantService.stopScreenShare(username, participantId));
    }

    @PostMapping("/leave")
    public ResponseEntity<?> leaveSession(@RequestBody Map<String, String> body,
                                          HttpServletRequest request) {
        String username = extractUsername(request);
        String sessionCode = body.get("sessionCode");

        return ResponseEntity.ok(participantService.leaveSession(username, sessionCode));
    }

    private ParticipantResponse toResponse(EducationSessionParticipant participant) {
        return new ParticipantResponse(
                participant.getId(),
                participant.getEducationSession().getSessionCode(),
                participant.getDisplayName(),
                participant.getStudent() != null ? participant.getStudent().getId() : null,
                participant.getStudent() != null ? participant.getStudent().getUsername() : null,
                participant.getStatus().name(),
                participant.getJoinedAt(),
                participant.getApprovedAt(),
                participant.isControlRequested(),
                participant.isHasControl(),
                participant.getControlRequestedAt(),
                participant.getControlGrantedAt(),
                participant.getLastActivityAt(),
                participant.isScreenShareRequested(),
                participant.isScreenShareActive(),
                participant.getScreenShareRequestedAt(),
                participant.getScreenShareStartedAt()
        );
    }

    private String extractUsername(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Authorization header is missing");
        }

        String token = authHeader.substring(7);

        if (!jwtUtil.validateToken(token)) {
            throw new IllegalArgumentException("Invalid JWT token");
        }

        return jwtUtil.extractUsername(token);
    }

    public record JoinEducationSessionRequest(
            String sessionCode,
            String displayName
    ) {
    }

    public record ControlRequest(
            String sessionCode
    ) {
    }

    public record ParticipantResponse(
            Long id,
            String sessionCode,
            String displayName,
            Long studentId,
            String username,
            String status,
            LocalDateTime joinedAt,
            LocalDateTime approvedAt,
            Boolean controlRequested,
            Boolean hasControl,
            LocalDateTime controlRequestedAt,
            LocalDateTime controlGrantedAt,
            LocalDateTime lastActivityAt,
            Boolean screenShareRequested,
            Boolean screenShareActive,
            LocalDateTime screenShareRequestedAt,
            LocalDateTime screenShareStartedAt
    ) {
    }
}