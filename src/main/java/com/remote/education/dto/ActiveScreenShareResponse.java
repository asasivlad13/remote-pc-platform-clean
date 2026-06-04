package com.remote.education.dto;

public record ActiveScreenShareResponse(
        Boolean active,
        Long participantId,
        Long studentId,
        String displayName,
        EducationAgentResponse agent
) {
}