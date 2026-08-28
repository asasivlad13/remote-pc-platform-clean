package com.remote.education.dto;

import com.remote.education.model.EducationSessionEventType;

import java.time.Instant;

public record EducationSessionEventResponse(
        Long id,
        EducationSessionEventType type,
        String message,
        String actorUsername,
        Instant createdAt
) {
}