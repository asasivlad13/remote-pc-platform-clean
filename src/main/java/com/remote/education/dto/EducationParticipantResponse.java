package com.remote.education.dto;

import java.time.LocalDateTime;

public record EducationParticipantResponse(
        Long id,
        String sessionCode,
        String displayName,
        Long studentId,
        String username,
        String status,
        LocalDateTime joinedAt,
        LocalDateTime approvedAt,
        Boolean controlRequested,
        Boolean hasControl,
        LocalDateTime controlRequestedAt,
        LocalDateTime controlGrantedAt,
        LocalDateTime lastActivityAt,
        Boolean screenShareRequested,
        Boolean screenShareActive,
        LocalDateTime screenShareRequestedAt,
        LocalDateTime screenShareStartedAt
) {
}