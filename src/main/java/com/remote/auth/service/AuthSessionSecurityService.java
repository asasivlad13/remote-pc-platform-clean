package com.remote.auth.service;

import com.remote.auth.model.AuthSession;
import com.remote.auth.model.AuthSessionRevokeReason;
import com.remote.auth.repository.AuthSessionRepository;
import com.remote.auth.security.JwtUtil;
import com.remote.core.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthSessionSecurityService {

    private final AuthSessionRepository sessionRepository;
    private final JwtUtil jwtUtil;

    public AuthSessionSecurityService(
            AuthSessionRepository sessionRepository,
            JwtUtil jwtUtil
    ) {
        this.sessionRepository =
                sessionRepository;

        this.jwtUtil =
                jwtUtil;
    }

    /*
     * Проверяет как legacy JWT, так и новый session access JWT.
     *
     * Legacy JWT не содержит token_type/sid и временно
     * принимается ради совместимости старого frontend и агента.
     *
     * Для session access JWT дополнительно проверяется
     * серверная auth_session.
     */
    @Transactional(readOnly = true)
    public Optional<String> validateAndExtractEmail(
            String rawToken
    ) {
        if (rawToken == null
                || rawToken.isBlank()) {

            return Optional.empty();
        }

        String token =
                rawToken.strip();

        if (!jwtUtil.validateToken(token)) {
            return Optional.empty();
        }

        String email;
        String tokenType;

        try {
            email =
                    jwtUtil.extractUsername(
                            token
                    );

            tokenType =
                    jwtUtil.extractTokenType(
                            token
                    );

        } catch (RuntimeException e) {
            return Optional.empty();
        }

        if (email == null
                || email.isBlank()) {

            return Optional.empty();
        }

        /*
         * Legacy JWT.
         */
        if (tokenType == null) {
            return Optional.of(
                    email
            );
        }

        /*
         * Любой неизвестный тип JWT отклоняется.
         */
        if (!JwtUtil.SESSION_ACCESS_TOKEN_TYPE
                .equals(tokenType)) {

            return Optional.empty();
        }

        UUID sessionId;

        try {
            sessionId =
                    jwtUtil.extractSessionId(
                            token
                    );

        } catch (RuntimeException e) {
            return Optional.empty();
        }

        AuthSession session =
                sessionRepository
                        .findById(
                                sessionId
                        )
                        .orElse(null);

        if (session == null
                || session.getRevokedAt() != null
                || session.getExpiresAt() == null
                || !Instant.now().isBefore(
                session.getExpiresAt()
        )) {
            return Optional.empty();
        }

        User sessionUser =
                session.getUser();

        if (sessionUser == null
                || sessionUser.getEmail() == null
                || !email.equals(
                sessionUser.getEmail()
        )) {
            return Optional.empty();
        }

        return Optional.of(
                email
        );
    }

    @Transactional
    public void revokeAllForUser(
            User user,
            AuthSessionRevokeReason reason,
            Instant revokedAt
    ) {
        if (user == null
                || user.getId() == null) {

            throw new IllegalArgumentException(
                    "Persisted user is required for auth session revocation"
            );
        }

        if (reason == null) {
            throw new IllegalArgumentException(
                    "Auth session revoke reason is required"
            );
        }

        Instant effectiveRevokedAt =
                revokedAt != null
                        ? revokedAt
                        : Instant.now();

        List<AuthSession> sessions =
                sessionRepository
                        .findByUserAndRevokedAtIsNullOrderByCreatedAtDesc(
                                user
                        );

        for (AuthSession session : sessions) {
            session.setRevokedAt(
                    effectiveRevokedAt
            );

            session.setRevocationReason(
                    reason
            );
        }

        if (!sessions.isEmpty()) {
            sessionRepository.saveAll(
                    sessions
            );
        }
    }
}