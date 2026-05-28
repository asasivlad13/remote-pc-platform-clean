package com.remote.education.controller;

import com.remote.auth.security.JwtUtil;
import com.remote.education.model.EducationSessionEvent;
import com.remote.education.model.EducationSessionEventType;
import com.remote.education.service.EducationSessionEventService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/education/events")
public class EducationSessionEventController {

    private final EducationSessionEventService eventService;
    private final JwtUtil jwtUtil;

    public EducationSessionEventController(EducationSessionEventService eventService,
                                           JwtUtil jwtUtil) {
        this.eventService = eventService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("/{sessionCode}")
    public List<EventResponse> getEvents(@PathVariable String sessionCode,
                                         HttpServletRequest request) {
        String username = extractUsername(request);

        return eventService.getEvents(username, sessionCode)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private EventResponse toResponse(EducationSessionEvent event) {
        return new EventResponse(
                event.getId(),
                event.getType(),
                event.getMessage(),
                event.getActor() != null ? event.getActor().getUsername() : null,
                event.getCreatedAt()
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

    public record EventResponse(
            Long id,
            EducationSessionEventType type,
            String message,
            String actorUsername,
            LocalDateTime createdAt
    ) {
    }
}