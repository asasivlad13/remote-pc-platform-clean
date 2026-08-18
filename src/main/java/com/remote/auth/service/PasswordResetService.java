package com.remote.auth.service;

import com.remote.auth.dto.AuthMessageResponse;
import com.remote.auth.dto.ForgotPasswordRequest;
import com.remote.auth.dto.ForgotPasswordResponse;
import com.remote.auth.dto.ResetPasswordRequest;
import com.remote.auth.model.PasswordResetToken;
import com.remote.auth.repository.PasswordResetTokenRepository;
import com.remote.core.model.AccountStatus;
import com.remote.core.model.User;
import com.remote.core.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class PasswordResetService {

    private static final Duration TOKEN_LIFETIME =
            Duration.ofMinutes(30);

    private static final String FORGOT_PASSWORD_MESSAGE =
            "If an account with this email exists, password reset instructions have been issued.";

    private static final String INVALID_TOKEN_MESSAGE =
            "Invalid or expired password reset token";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final SecureTokenService secureTokenService;
    private final PasswordPolicyService passwordPolicyService;

    private final boolean exposeResetToken;

    private final BCryptPasswordEncoder encoder =
            new BCryptPasswordEncoder();

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            SecureTokenService secureTokenService,
            PasswordPolicyService passwordPolicyService,
            @Value("${auth.dev.expose-reset-token:false}")
            boolean exposeResetToken
    ) {
        this.userRepository =
                userRepository;

        this.tokenRepository =
                tokenRepository;

        this.secureTokenService =
                secureTokenService;

        this.passwordPolicyService =
                passwordPolicyService;

        this.exposeResetToken =
                exposeResetToken;
    }

    @Transactional
    public ForgotPasswordResponse requestReset(
            ForgotPasswordRequest request
    ) {
        String email =
                normalizeEmail(
                        request.email()
                );

        /*
         * Генерируем token в любом случае.
         *
         * Даже для несуществующего аккаунта dev-ответ
         * имеет одинаковую форму и не раскрывает наличие email.
         */
        String rawToken =
                secureTokenService
                        .generateToken();

        User user =
                userRepository
                        .findByEmailForUpdate(
                                email
                        )
                        .orElse(null);

        if (user != null
                && canResetPassword(user)) {

            Instant now =
                    Instant.now();

            revokeExistingTokens(
                    user,
                    now
            );

            PasswordResetToken token =
                    new PasswordResetToken();

            token.setUser(
                    user
            );

            token.setTokenHash(
                    secureTokenService
                            .hashToken(
                                    rawToken
                            )
            );

            token.setExpiresAt(
                    now.plus(
                            TOKEN_LIFETIME
                    )
            );

            tokenRepository.save(
                    token
            );
        }

        return new ForgotPasswordResponse(
                FORGOT_PASSWORD_MESSAGE,
                exposeResetToken
                        ? rawToken
                        : null
        );
    }

    @Transactional
    public AuthMessageResponse resetPassword(
            ResetPasswordRequest request
    ) {
        String rawToken =
                request.token()
                        .strip();

        String tokenHash;

        try {
            tokenHash =
                    secureTokenService
                            .hashToken(
                                    rawToken
                            );

        } catch (IllegalArgumentException e) {
            throw invalidToken();
        }

        /*
         * Первое чтение НЕ изменяет данные.
         *
         * Оно нужно, чтобы определить владельца token,
         * после чего сначала блокируется User.
         */
        PasswordResetToken preview =
                tokenRepository
                        .findByTokenHash(
                                tokenHash
                        )
                        .orElseThrow(
                                this::invalidToken
                        );

        if (preview.getUser() == null
                || preview.getUser()
                .getId() == null) {

            throw invalidToken();
        }

        Long userId =
                preview.getUser()
                        .getId();

        /*
         * Единый порядок блокировок security-flow:
         *
         * 1. User
         * 2. PasswordResetToken
         */
        User user =
                userRepository
                        .findByIdForUpdate(
                                userId
                        )
                        .orElseThrow(
                                this::invalidToken
                        );

        PasswordResetToken token =
                tokenRepository
                        .findByTokenHashForUpdate(
                                tokenHash
                        )
                        .orElseThrow(
                                this::invalidToken
                        );

        if (token.getUser() == null
                || !Objects.equals(
                token.getUser().getId(),
                user.getId()
        )) {
            throw invalidToken();
        }

        Instant now =
                Instant.now();

        validateToken(
                token,
                now
        );

        if (!canResetPassword(user)) {
            throw invalidToken();
        }

        /*
         * Используем ту же политику паролей,
         * что и регистрация.
         */
        passwordPolicyService
                .validateRegistrationPassword(
                        user.getEmail(),
                        request.newPassword(),
                        request.confirmPassword()
                );

        /*
         * Пока User ещё заблокирован этой транзакцией,
         * получаем все остальные действующие reset-токены.
         */
        List<PasswordResetToken> activeTokens =
                tokenRepository
                        .findByUserAndUsedAtIsNullAndRevokedAtIsNull(
                                user
                        );

        user.setPassword(
                encoder.encode(
                        request.newPassword()
                )
        );

        user.setPasswordChangedAt(
                now
        );

        token.setUsedAt(
                now
        );

        for (PasswordResetToken activeToken
                : activeTokens) {

            if (!Objects.equals(
                    activeToken.getId(),
                    token.getId()
            )) {
                activeToken.setRevokedAt(
                        now
                );
            }
        }

        userRepository.save(
                user
        );

        tokenRepository.save(
                token
        );

        List<PasswordResetToken> revokedTokens =
                activeTokens.stream()
                        .filter(
                                activeToken ->
                                        !Objects.equals(
                                                activeToken.getId(),
                                                token.getId()
                                        )
                        )
                        .toList();

        if (!revokedTokens.isEmpty()) {
            tokenRepository.saveAll(
                    revokedTokens
            );
        }

        return new AuthMessageResponse(
                "Password reset successfully"
        );
    }

    private void validateToken(
            PasswordResetToken token,
            Instant now
    ) {
        if (token.getUsedAt() != null
                || token.getRevokedAt() != null
                || token.getExpiresAt() == null
                || !now.isBefore(
                token.getExpiresAt()
        )) {
            throw invalidToken();
        }
    }

    private boolean canResetPassword(
            User user
    ) {
        return user.getStatus()
                == AccountStatus.ACTIVE

                || user.getStatus()
                == AccountStatus.EMAIL_NOT_VERIFIED;
    }

    private void revokeExistingTokens(
            User user,
            Instant now
    ) {
        List<PasswordResetToken> tokens =
                tokenRepository
                        .findByUserAndUsedAtIsNullAndRevokedAtIsNull(
                                user
                        );

        for (PasswordResetToken token : tokens) {
            token.setRevokedAt(
                    now
            );
        }

        if (!tokens.isEmpty()) {
            tokenRepository.saveAll(
                    tokens
            );
        }
    }

    private String normalizeEmail(
            String value
    ) {
        if (value == null
                || value.isBlank()) {

            return "";
        }

        return value.strip()
                .toLowerCase(
                        Locale.ROOT
                );
    }

    private ResponseStatusException invalidToken() {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                INVALID_TOKEN_MESSAGE
        );
    }
}