package com.remote.education.dto;

import java.time.Instant;

public record EducationParticipantResponse(
        Long id,
        String sessionCode,
        String displayName,
        Long studentId,
        String username,
        String status,
        Instant joinedAt,
        Instant approvedAt,
        Boolean controlRequested,
        Boolean hasControl,
        Instant controlRequestedAt,
        Instant controlGrantedAt,
        Instant lastActivityAt,
        Boolean screenShareRequested,
        Boolean screenShareActive,
        Instant screenShareRequestedAt,
        Instant screenShareStartedAt
) {
}