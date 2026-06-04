package com.remote.education.dto;

import java.time.LocalDateTime;

public record EducationTimelineEventResponse(
        Long id,
        String type,
        String actorUsername,
        String message,
        LocalDateTime createdAt
) {
}