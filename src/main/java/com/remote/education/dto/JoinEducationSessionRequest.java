package com.remote.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JoinEducationSessionRequest(
        @NotBlank
        @Size(min = 6, max = 6)
        String sessionCode,

        @Size(max = 100)
        String displayName
) {
}