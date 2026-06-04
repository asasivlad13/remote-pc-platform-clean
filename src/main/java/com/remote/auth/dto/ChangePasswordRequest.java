package com.remote.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank
        String oldPassword,

        @NotBlank
        @Size(min = 6, max = 255)
        String newPassword
) {
}