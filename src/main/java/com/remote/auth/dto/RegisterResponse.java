package com.remote.auth.dto;

public record RegisterResponse(
        String message,
        String verificationToken
) {
}