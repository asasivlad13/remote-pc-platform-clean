package com.remote.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(

        String email,

        String username,

        @NotBlank
        @Size(max = 255)
        String password
) {

    public String identifier() {
        if (email != null && !email.isBlank()) {
            return email;
        }

        return username;
    }
}