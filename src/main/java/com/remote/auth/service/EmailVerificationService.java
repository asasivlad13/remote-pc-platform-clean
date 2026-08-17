package com.remote.auth.service;

import com.remote.auth.model.EmailVerificationToken;
import com.remote.auth.repository.EmailVerificationTokenRepository;
import com.remote.core.model.AccountStatus;
import com.remote.core.model.User;
import com.remote.core.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Service
public class EmailVerificationService {

    private static final Duration TOKEN_LIFETIME =
            Duration.ofMinutes(30);

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom =
            new SecureRandom();

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;

    public EmailVerificationService(
            EmailVerificationTokenRepository tokenRepository,
            UserRepository userRepository
    ) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public String createToken(User user) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException(
                    "User must be persisted before email verification token creation"
            );
        }

        if (user.getStatus() == AccountStatus.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Email is already verified"
            );
        }

        revokeExistingTokens(user);

        String rawToken = generateToken();

        EmailVerificationToken token =
                new EmailVerificationToken();

        token.setUser(user);
        token.setTokenHash(hashToken(rawToken));
        token.setExpiresAt(
                Instant.now().plus(TOKEN_LIFETIME)
        );

        tokenRepository.save(token);

        return rawToken;
    }

    @Transactional
    public void verify(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Verification token is required"
            );
        }

        String tokenHash =
                hashToken(rawToken.strip());

        EmailVerificationToken token =
                tokenRepository
                        .findByTokenHash(tokenHash)
                        .orElseThrow(
                                () -> new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Invalid verification token"
                                )
                        );

        Instant now = Instant.now();

        if (token.getUsedAt() != null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Verification token has already been used"
            );
        }

        if (token.getRevokedAt() != null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Verification token has been revoked"
            );
        }

        if (!now.isBefore(token.getExpiresAt())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Verification token has expired"
            );
        }

        User user = token.getUser();

        if (user == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Verification token has no user"
            );
        }

        if (user.getStatus() == AccountStatus.BLOCKED
                || user.getStatus() == AccountStatus.DISABLED) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Account cannot be activated"
            );
        }

        user.setStatus(AccountStatus.ACTIVE);
        user.setEmailVerifiedAt(now);

        token.setUsedAt(now);

        userRepository.save(user);
        tokenRepository.save(token);

        revokeOtherTokens(
                user,
                token,
                now
        );
    }

    private void revokeExistingTokens(User user) {
        Instant now = Instant.now();

        List<EmailVerificationToken> tokens =
                tokenRepository
                        .findByUserAndUsedAtIsNullAndRevokedAtIsNull(
                                user
                        );

        for (EmailVerificationToken token : tokens) {
            token.setRevokedAt(now);
        }

        if (!tokens.isEmpty()) {
            tokenRepository.saveAll(tokens);
        }
    }

    private void revokeOtherTokens(
            User user,
            EmailVerificationToken usedToken,
            Instant now
    ) {
        List<EmailVerificationToken> tokens =
                tokenRepository
                        .findByUserAndUsedAtIsNullAndRevokedAtIsNull(
                                user
                        );

        for (EmailVerificationToken token : tokens) {
            if (!token.getId().equals(
                    usedToken.getId()
            )) {
                token.setRevokedAt(now);
            }
        }

        tokenRepository.saveAll(tokens);
    }

    private String generateToken() {
        byte[] bytes =
                new byte[TOKEN_BYTES];

        secureRandom.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            byte[] hash =
                    digest.digest(
                            rawToken.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            return HexFormat.of()
                    .formatHex(hash);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 is not available",
                    e
            );
        }
    }
}