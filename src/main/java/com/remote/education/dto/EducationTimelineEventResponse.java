package com.remote.education.dto;

import java.time.Instant;

public record EducationTimelineEventResponse(
        Long id,
        String type,
        String actorUsername,
        String message,
        Instant createdAt
) {
}