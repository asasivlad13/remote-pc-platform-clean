package com.remote.auth.dto;

public record ForgotPasswordResponse(
        String message,

        /*
         * Только для локальной разработки.
         *
         * В production должен быть null,
         * а настоящий token отправляется по email.
         */
        String resetToken
) {
}