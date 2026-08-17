package com.remote.auth.repository;

import com.remote.auth.model.EmailVerificationToken;
import com.remote.core.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmailVerificationTokenRepository
        extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByTokenHash(
            String tokenHash
    );

    List<EmailVerificationToken>
    findByUserAndUsedAtIsNullAndRevokedAtIsNull(
            User user
    );
}