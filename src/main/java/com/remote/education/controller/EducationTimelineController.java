package com.remote.education.controller;

import com.remote.core.service.CurrentUserService;
import com.remote.education.dto.EducationTimelineEventResponse;
import com.remote.education.service.EducationTimelineService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/education/sessions")
public class EducationTimelineController {

    private final EducationTimelineService timelineService;
    private final CurrentUserService currentUserService;

    @GetMapping("/{sessionCode}/timeline")
    public List<EducationTimelineEventResponse> getTimeline(@PathVariable String sessionCode,
                                                            HttpServletRequest request) {
        String username = currentUserService.extractUsername(request);
        return timelineService.getTimeline(username, sessionCode);
    }
}