package com.remote.auth.service;

import com.remote.auth.dto.LoginRequest;
import com.remote.auth.model.AuthSession;
import com.remote.auth.model.AuthSessionRevokeReason;
import com.remote.auth.repository.AuthSessionRepository;
import com.remote.auth.security.JwtUtil;
import com.remote.core.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class AuthSessionService {

    public static final String REFRESH_COOKIE_NAME =
            "remote_refresh_token";

    private static final String INVALID_REFRESH_MESSAGE =
            "Invalid or expired refresh session";

    private static final int IP_MAX_LENGTH = 45;
    private static final int USER_AGENT_MAX_LENGTH = 512;

    private final AuthSessionRepository sessionRepository;
    private final AuthService authService;
    private final JwtUtil jwtUtil;
    private final SecureTokenService secureTokenService;

    private final long refreshLifetimeDays;

    public AuthSessionService(
            AuthSessionRepository sessionRepository,
            AuthService authService,
            JwtUtil jwtUtil,
            SecureTokenService secureTokenService,
            @Value("${auth.session.refresh-lifetime-days:30}")
            long refreshLifetimeDays
    ) {
        if (refreshLifetimeDays <= 0) {
            throw new IllegalArgumentException(
                    "auth.session.refresh-lifetime-days must be positive"
            );
        }

        this.sessionRepository =
                sessionRepository;

        this.authService =
                authService;

        this.jwtUtil =
                jwtUtil;

        this.secureTokenService =
                secureTokenService;

        this.refreshLifetimeDays =
                refreshLifetimeDays;
    }

    @Transactional(
            noRollbackFor = ResponseStatusException.class
    )
    public SessionTokens login(
            LoginRequest request,
            String ipAddress,
            String userAgent
    ) {
        User user =
                authService.authenticateCredentials(
                        request,
                        ipAddress
                );

        Instant now =
                Instant.now();

        UUID sessionId =
                UUID.randomUUID();

        int refreshVersion = 1;

        String rawRefreshToken =
                generateRefreshToken(
                        sessionId,
                        refreshVersion
                );

        AuthSession session =
                new AuthSession();

        session.setId(
                sessionId
        );

        session.setUser(
                user
        );

        session.setRefreshTokenVersion(
                refreshVersion
        );

        session.setRefreshTokenHash(
                secureTokenService.hashToken(
                        rawRefreshToken
                )
        );

        session.setIpAddress(
                normalizeMetadata(
                        ipAddress,
                        IP_MAX_LENGTH
                )
        );

        session.setUserAgent(
                normalizeMetadata(
                        userAgent,
                        USER_AGENT_MAX_LENGTH
                )
        );

        session.setCreatedAt(
                now
        );

        session.setExpiresAt(
                now.plus(
                        Duration.ofDays(
                                refreshLifetimeDays
                        )
                )
        );

        sessionRepository.save(
                session
        );

        String accessToken =
                jwtUtil.generateSessionAccessToken(
                        user.getEmail(),
                        sessionId
                );

        return new SessionTokens(
                accessToken,
                rawRefreshToken,
                sessionId,
                session.getExpiresAt()
        );
    }

    @Transactional
    public SessionTokens refresh(
            String rawRefreshToken
    ) {
        String normalizedToken =
                normalizeRequiredRefreshToken(
                        rawRefreshToken
                );

        ParsedRefreshToken parsedToken =
                parseRefreshToken(
                        normalizedToken
                );

        AuthSession session =
                sessionRepository
                        .findByIdForUpdate(
                                parsedToken.sessionId()
                        )
                        .orElseThrow(
                                this::invalidRefresh
                        );

        Instant now =
                Instant.now();

        validateRefreshSession(
                session,
                parsedToken,
                normalizedToken,
                now
        );

        int nextVersion;

        try {
            nextVersion =
                    Math.addExact(
                            session.getRefreshTokenVersion(),
                            1
                    );

        } catch (ArithmeticException e) {
            throw invalidRefresh();
        }

        String nextRefreshToken =
                generateRefreshToken(
                        session.getId(),
                        nextVersion
                );

        session.setRefreshTokenVersion(
                nextVersion
        );

        session.setRefreshTokenHash(
                secureTokenService.hashToken(
                        nextRefreshToken
                )
        );

        session.setLastUsedAt(
                now
        );

        sessionRepository.save(
                session
        );

        String accessToken =
                jwtUtil.generateSessionAccessToken(
                        session.getUser().getEmail(),
                        session.getId()
                );

        return new SessionTokens(
                accessToken,
                nextRefreshToken,
                session.getId(),
                session.getExpiresAt()
        );
    }

    @Transactional
    public void logout(
            String rawRefreshToken
    ) {
        if (rawRefreshToken == null
                || rawRefreshToken.isBlank()) {

            return;
        }

        String normalizedToken =
                rawRefreshToken.strip();

        ParsedRefreshToken parsedToken;

        try {
            parsedToken =
                    parseRefreshToken(
                            normalizedToken
                    );

        } catch (ResponseStatusException e) {
            return;
        }

        AuthSession session =
                sessionRepository
                        .findByIdForUpdate(
                                parsedToken.sessionId()
                        )
                        .orElse(null);

        if (session == null
                || session.getRevokedAt() != null) {

            return;
        }

        if (!matchesCurrentRefreshToken(
                session,
                parsedToken,
                normalizedToken
        )) {
            /*
             * Старый или чужой refresh token
             * не должен иметь возможность отозвать
             * текущую актуальную сессию.
             */
            return;
        }

        session.setRevokedAt(
                Instant.now()
        );

        session.setRevocationReason(
                AuthSessionRevokeReason.LOGOUT
        );

        sessionRepository.save(
                session
        );
    }

    private void validateRefreshSession(
            AuthSession session,
            ParsedRefreshToken parsedToken,
            String rawRefreshToken,
            Instant now
    ) {
        if (session.getRevokedAt() != null) {
            throw invalidRefresh();
        }

        if (session.getExpiresAt() == null
                || !now.isBefore(
                session.getExpiresAt()
        )) {
            throw invalidRefresh();
        }

        User user =
                session.getUser();

        if (user == null
                || !user.isEnabled()
                || !user.isAccountNonLocked()
                || !user.isAccountNonExpired()
                || !user.isCredentialsNonExpired()) {

            throw invalidRefresh();
        }

        if (!matchesCurrentRefreshToken(
                session,
                parsedToken,
                rawRefreshToken
        )) {
            /*
             * В первой реализации stale refresh token
             * просто отклоняется.
             *
             * Автоматически отзывать всю сессию здесь
             * нельзя: это может быть обычный параллельный
             * refresh из другой вкладки.
             */
            throw invalidRefresh();
        }
    }

    private boolean matchesCurrentRefreshToken(
            AuthSession session,
            ParsedRefreshToken parsedToken,
            String rawRefreshToken
    ) {
        if (session.getRefreshTokenVersion() == null
                || session.getRefreshTokenVersion()
                != parsedToken.version()) {

            return false;
        }

        String actualHash =
                secureTokenService.hashToken(
                        rawRefreshToken
                );

        String expectedHash =
                session.getRefreshTokenHash();

        if (expectedHash == null
                || expectedHash.isBlank()) {

            return false;
        }

        return MessageDigest.isEqual(
                expectedHash.getBytes(
                        StandardCharsets.US_ASCII
                ),
                actualHash.getBytes(
                        StandardCharsets.US_ASCII
                )
        );
    }

    private String generateRefreshToken(
            UUID sessionId,
            int version
    ) {
        return sessionId
                + "."
                + version
                + "."
                + secureTokenService.generateToken();
    }

    private ParsedRefreshToken parseRefreshToken(
            String rawRefreshToken
    ) {
        try {
            String[] parts =
                    rawRefreshToken.split(
                            "\\.",
                            3
                    );

            if (parts.length != 3
                    || parts[2].isBlank()) {

                throw invalidRefresh();
            }

            UUID sessionId =
                    UUID.fromString(
                            parts[0]
                    );

            int version =
                    Integer.parseInt(
                            parts[1]
                    );

            if (version < 1) {
                throw invalidRefresh();
            }

            return new ParsedRefreshToken(
                    sessionId,
                    version
            );

        } catch (IllegalArgumentException e) {
            throw invalidRefresh();
        }
    }

    private String normalizeRequiredRefreshToken(
            String rawRefreshToken
    ) {
        if (rawRefreshToken == null
                || rawRefreshToken.isBlank()) {

            throw invalidRefresh();
        }

        return rawRefreshToken.strip();
    }

    private String normalizeMetadata(
            String value,
            int maxLength
    ) {
        if (value == null
                || value.isBlank()) {

            return null;
        }

        String normalized =
                value.strip();

        if (normalized.length() <= maxLength) {
            return normalized;
        }

        return normalized.substring(
                0,
                maxLength
        );
    }

    private ResponseStatusException invalidRefresh() {
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                INVALID_REFRESH_MESSAGE
        );
    }

    private record ParsedRefreshToken(
            UUID sessionId,
            int version
    ) {
    }

    public record SessionTokens(
            String accessToken,
            String refreshToken,
            UUID sessionId,
            Instant refreshExpiresAt
    ) {
    }
}