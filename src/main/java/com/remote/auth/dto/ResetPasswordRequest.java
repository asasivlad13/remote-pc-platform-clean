package com.remote.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(

        @NotBlank
        @Size(min = 32, max = 512)
        String token,

        @NotBlank
        @Size(min = 12, max = 64)
        String newPassword,

        @NotBlank
        @Size(min = 12, max = 64)
        String confirmPassword

) {
}