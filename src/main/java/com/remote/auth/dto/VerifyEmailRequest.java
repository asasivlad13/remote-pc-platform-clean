package com.remote.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyEmailRequest(

        @NotBlank
        @Size(min = 32, max = 512)
        String token
) {
}