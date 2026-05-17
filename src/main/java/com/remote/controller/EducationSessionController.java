package com.remote.controller;

import com.remote.config.JwtUtil;
import com.remote.model.EducationSession;
import com.remote.service.EducationSessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/education/sessions")
public class EducationSessionController {

    private final EducationSessionService educationSessionService;
    private final JwtUtil jwtUtil;

    public EducationSessionController(EducationSessionService educationSessionService,
                                      JwtUtil jwtUtil) {
        this.educationSessionService = educationSessionService;
        this.jwtUtil = jwtUtil;
    }
    @GetMapping("/my-active/teacher")
    public ResponseEntity<?> getMyActiveTeacherSession() {
        return educationSessionService.getMyActiveTeacherSession()
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/my-active/student")
    public ResponseEntity<?> getMyActiveStudentSession() {
        return educationSessionService.getMyActiveStudentSession()
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping
    public EducationSessionResponse createSession(@RequestBody CreateEducationSessionRequest request,
                                                  HttpServletRequest httpRequest) {
        String username = extractUsername(httpRequest);

        EducationSession session = educationSessionService.createSession(
                username,
                request.teacherPcId(),
                request.title(),
                request.maxStudents(),
                request.allowStudentControl(),
                request.allowFileTransfer(),
                request.allowStudentScreenShare()
        );

        return toResponse(session);
    }

    @GetMapping("/my")
    public List<EducationSessionResponse> getMySessions(HttpServletRequest request) {
        String username = extractUsername(request);

        return educationSessionService.getMySessions(username)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{sessionCode}")
    public EducationSessionResponse getByCode(@PathVariable String sessionCode) {
        return toResponse(educationSessionService.getByCode(sessionCode));
    }

    @PostMapping("/{sessionCode}/finish")
    public EducationSessionResponse finishSession(@PathVariable String sessionCode,
                                                  HttpServletRequest request) {
        String username = extractUsername(request);
        return toResponse(educationSessionService.finishSession(username, sessionCode));
    }

    private EducationSessionResponse toResponse(EducationSession session) {
        return new EducationSessionResponse(
                session.getId(),
                session.getSessionCode(),
                session.getTitle(),
                session.getStatus().name(),
                session.getTeacher() != null ? session.getTeacher().getUsername() : null,
                session.getTeacherPc() != null ? session.getTeacherPc().getId() : null,
                session.getTeacherPc() != null ? session.getTeacherPc().getName() : null,
                session.getTeacherPc() != null ? session.getTeacherPc().getWebrtcUrl() : null,
                session.getTeacherPc() != null ? session.getTeacherPc().getStreamName() : null,
                session.getTeacherPc() != null ? session.getTeacherPc().getScreenWidth() : null,
                session.getTeacherPc() != null ? session.getTeacherPc().getScreenHeight() : null,
                session.getMaxStudents(),
                session.getAllowStudentControl(),
                session.getAllowFileTransfer(),
                session.getAllowStudentScreenShare(),
                session.getCreatedAt(),
                session.getFinishedAt()
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

    public record CreateEducationSessionRequest(
            Long teacherPcId,
            String title,
            Integer maxStudents,
            Boolean allowStudentControl,
            Boolean allowFileTransfer,
            Boolean allowStudentScreenShare
    ) {
    }

    public record EducationSessionResponse(
            Long id,
            String sessionCode,
            String title,
            String status,
            String teacherUsername,
            Long teacherPcId,
            String teacherPcName,
            String teacherPcWebrtcUrl,
            String teacherPcStreamName,
            Integer teacherPcScreenWidth,
            Integer teacherPcScreenHeight,
            Integer maxStudents,
            Boolean allowStudentControl,
            Boolean allowFileTransfer,
            Boolean allowStudentScreenShare,
            LocalDateTime createdAt,
            LocalDateTime finishedAt
    ) {
    }
}