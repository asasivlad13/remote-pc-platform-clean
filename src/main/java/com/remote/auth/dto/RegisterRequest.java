package com.remote.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank
        @Email
        @Size(max = 254)
        String email,

        @NotBlank
        @Size(min = 1, max = 100)
        String displayName,

        @NotBlank
        @Size(min = 12, max = 64)
        String password,

        @NotBlank
        @Size(min = 12, max = 64)
        String confirmPassword
) {
}