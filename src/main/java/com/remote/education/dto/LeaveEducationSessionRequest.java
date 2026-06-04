package com.remote.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LeaveEducationSessionRequest(
        @NotBlank
        @Size(min = 6, max = 6)
        String sessionCode
) {
}