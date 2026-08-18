package com.remote.auth.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

@Service
public class PasswordPolicyService {

    private static final Set<String> COMMON_PASSWORDS =
            Set.of(
                    "password123!",
                    "qwerty123456!",
                    "admin123456!",
                    "welcome123!",
                    "letmein123!"
            );

    public void validateRegistrationPassword(
            String email,
            String password,
            String confirmPassword
    ) {
        if (!password.equals(confirmPassword)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Passwords do not match"
            );
        }

        validatePasswordStrength(
                email,
                password
        );
    }

    public void validatePasswordStrength(
            String email,
            String password
    ) {
        if (password == null
                || password.length() < 12) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password must contain at least 12 characters"
            );
        }

        if (password.length() > 64) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password is too long"
            );
        }

        /*
         * BCrypt учитывает только первые 72 байта.
         * Поэтому ограничение проверяется именно
         * по UTF-8 представлению пароля.
         */
        if (password
                .getBytes(StandardCharsets.UTF_8)
                .length > 72) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password is too long for the current password encoder"
            );
        }

        boolean hasLowercase =
                password.chars()
                        .anyMatch(
                                Character::isLowerCase
                        );

        boolean hasUppercase =
                password.chars()
                        .anyMatch(
                                Character::isUpperCase
                        );

        boolean hasDigit =
                password.chars()
                        .anyMatch(
                                Character::isDigit
                        );

        boolean hasSpecial =
                password.chars()
                        .anyMatch(
                                character ->
                                        !Character.isLetterOrDigit(
                                                character
                                        )
                        );

        if (!hasLowercase
                || !hasUppercase
                || !hasDigit
                || !hasSpecial) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password must contain lowercase and uppercase letters, a digit and a special character"
            );
        }

        if (email != null
                && password.equalsIgnoreCase(email)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password must not be the same as email"
            );
        }

        String normalizedPassword =
                password.toLowerCase(
                        Locale.ROOT
                );

        if (COMMON_PASSWORDS.contains(
                normalizedPassword
        )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password is too common"
            );
        }
    }
}