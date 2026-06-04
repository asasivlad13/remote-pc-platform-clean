package com.remote.education.controller;

import com.remote.core.service.CurrentUserService;
import com.remote.education.dto.EducationSessionStatisticsResponse;
import com.remote.education.service.EducationStatisticsService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/education/sessions")
public class EducationStatisticsController {

    private final EducationStatisticsService statisticsService;
    private final CurrentUserService currentUserService;

    @GetMapping("/{sessionCode}/statistics")
    public EducationSessionStatisticsResponse getStatistics(@PathVariable String sessionCode,
                                                            HttpServletRequest request) {
        String username = currentUserService.extractUsername(request);
        return statisticsService.getStatistics(username, sessionCode);
    }
}