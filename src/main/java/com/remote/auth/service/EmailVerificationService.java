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

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class EmailVerificationService {

    private static final Duration TOKEN_LIFETIME =
            Duration.ofMinutes(30);

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final SecureTokenService secureTokenService;

    public EmailVerificationService(
            EmailVerificationTokenRepository tokenRepository,
            UserRepository userRepository,
            SecureTokenService secureTokenService
    ) {
        this.tokenRepository =
                tokenRepository;

        this.userRepository =
                userRepository;

        this.secureTokenService =
                secureTokenService;
    }

    @Transactional
    public String createToken(
            User user
    ) {
        if (user == null
                || user.getId() == null) {

            throw new IllegalArgumentException(
                    "User must be persisted before email verification token creation"
            );
        }

        if (user.getStatus()
                == AccountStatus.ACTIVE) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Email is already verified"
            );
        }

        revokeExistingTokens(
                user
        );

        String rawToken =
                secureTokenService
                        .generateToken();

        EmailVerificationToken token =
                new EmailVerificationToken();

        token.setUser(user);

        token.setTokenHash(
                secureTokenService
                        .hashToken(
                                rawToken
                        )
        );

        token.setExpiresAt(
                Instant.now()
                        .plus(
                                TOKEN_LIFETIME
                        )
        );

        tokenRepository.save(
                token
        );

        return rawToken;
    }

    @Transactional
    public void verify(
            String rawToken
    ) {
        if (rawToken == null
                || rawToken.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Verification token is required"
            );
        }

        String tokenHash =
                secureTokenService
                        .hashToken(
                                rawToken.strip()
                        );

        EmailVerificationToken token =
                tokenRepository
                        .findByTokenHashForUpdate(
                                tokenHash
                        )
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST,
                                                "Invalid verification token"
                                        )
                        );

        Instant now =
                Instant.now();

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

        if (!now.isBefore(
                token.getExpiresAt()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Verification token has expired"
            );
        }

        User user =
                token.getUser();

        if (user == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Verification token has no user"
            );
        }

        if (user.getStatus()
                == AccountStatus.BLOCKED

                || user.getStatus()
                == AccountStatus.DISABLED) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Account cannot be activated"
            );
        }

        user.setStatus(
                AccountStatus.ACTIVE
        );

        user.setEmailVerifiedAt(
                now
        );

        token.setUsedAt(
                now
        );

        userRepository.save(
                user
        );

        tokenRepository.save(
                token
        );

        revokeOtherTokens(
                user,
                token,
                now
        );
    }

    private void revokeExistingTokens(
            User user
    ) {
        Instant now =
                Instant.now();

        List<EmailVerificationToken> tokens =
                tokenRepository
                        .findByUserAndUsedAtIsNullAndRevokedAtIsNull(
                                user
                        );

        for (EmailVerificationToken token : tokens) {
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
            if (!token.getId()
                    .equals(
                            usedToken.getId()
                    )) {

                token.setRevokedAt(
                        now
                );
            }
        }

        if (!tokens.isEmpty()) {
            tokenRepository.saveAll(
                    tokens
            );
        }
    }
}