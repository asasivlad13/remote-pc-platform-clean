package com.remote.education.controller;

import com.remote.core.service.CurrentUserService;
import com.remote.education.dto.EducationSessionEventResponse;
import com.remote.education.service.EducationSessionEventService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/education/events")
public class EducationSessionEventController {

    private final EducationSessionEventService eventService;
    private final CurrentUserService currentUserService;

    @GetMapping("/{sessionCode}")
    public List<EducationSessionEventResponse> getEvents(@PathVariable String sessionCode,
                                                         HttpServletRequest request) {
        String username = currentUserService.extractUsername(request);
        return eventService.getEventResponses(username, sessionCode);
    }
}