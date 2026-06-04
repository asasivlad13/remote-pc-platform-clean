package com.remote.education.controller;

import com.remote.core.service.CurrentUserService;
import com.remote.education.dto.CreateEducationSessionRequest;
import com.remote.education.dto.EducationSessionResponse;
import com.remote.education.service.EducationSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/education/sessions")
public class EducationSessionController {

    private final EducationSessionService educationSessionService;
    private final CurrentUserService currentUserService;

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
    public EducationSessionResponse createSession(@Valid @RequestBody CreateEducationSessionRequest request,
                                                  HttpServletRequest httpRequest) {
        String username = currentUserService.extractUsername(httpRequest);

        return educationSessionService.createSessionResponse(
                username,
                request.teacherPcId(),
                request.title(),
                request.teacherDisplayName(),
                request.maxStudents(),
                request.allowStudentControl(),
                request.allowFileTransfer(),
                request.allowStudentScreenShare()
        );
    }

    @GetMapping("/my")
    public List<EducationSessionResponse> getMySessions(HttpServletRequest request) {
        String username = currentUserService.extractUsername(request);
        return educationSessionService.getMySessionResponses(username);
    }

    @GetMapping("/{sessionCode}")
    public EducationSessionResponse getByCode(@PathVariable String sessionCode) {
        return educationSessionService.getByCodeResponse(sessionCode);
    }

    @PostMapping("/{sessionCode}/finish")
    public EducationSessionResponse finishSession(@PathVariable String sessionCode,
                                                  HttpServletRequest request) {
        String username = currentUserService.extractUsername(request);
        return educationSessionService.finishSessionResponse(username, sessionCode);
    }
}