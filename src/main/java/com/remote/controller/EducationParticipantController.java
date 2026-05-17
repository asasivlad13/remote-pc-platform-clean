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

    private final EducationParticipantService educationParticipantService;
    private final EducationParticipantService participantService;
    private final JwtUtil jwtUtil;

    public EducationParticipantController(EducationParticipantService educationParticipantService, EducationParticipantService participantService,
                                          JwtUtil jwtUtil) {
        this.educationParticipantService = educationParticipantService;
        this.participantService = participantService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("/status/{participantId}")
    public ParticipantResponse getStatus(@PathVariable Long participantId,
                                         HttpServletRequest request) {
        String username = extractUsername(request);
        return toResponse(participantService.getMyParticipantStatus(username, participantId));
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

    public record ControlRequest(
            String sessionCode
    ) {
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
    @PostMapping("/leave")
    public ResponseEntity<?> leaveSession(@RequestBody Map<String, String> body) {
        String sessionCode = body.get("sessionCode");
        return ResponseEntity.ok(educationParticipantService.leaveSession(sessionCode));
    }
    private ParticipantResponse toResponse(EducationSessionParticipant participant) {
        return new ParticipantResponse(
                participant.getId(),
                participant.getEducationSession().getSessionCode(),
                participant.getDisplayName(),
                participant.getStudent() != null ? participant.getStudent().getUsername() : null,
                participant.getStatus().name(),
                participant.getJoinedAt(),
                participant.getApprovedAt(),
                participant.isControlRequested(),
                participant.isHasControl(),
                participant.getControlRequestedAt(),
                participant.getControlGrantedAt()
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

    public record ParticipantResponse(
            Long id,
            String sessionCode,
            String displayName,
            String username,
            String status,
            LocalDateTime joinedAt,
            LocalDateTime approvedAt,
            Boolean controlRequested,
            Boolean hasControl,
            LocalDateTime controlRequestedAt,
            LocalDateTime controlGrantedAt
    ) {
    }
}