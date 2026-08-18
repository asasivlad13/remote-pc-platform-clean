package com.remote.auth.repository;

import com.remote.auth.model.EmailVerificationToken;
import com.remote.core.model.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmailVerificationTokenRepository
        extends JpaRepository<EmailVerificationToken, Long> {

    /*
     * Verification-токен является одноразовым.
     *
     * Блокировка строки гарантирует, что два
     * параллельных запроса не смогут одновременно
     * успешно использовать один и тот же токен.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select token
            from EmailVerificationToken token
            where token.tokenHash = :tokenHash
            """)
    Optional<EmailVerificationToken> findByTokenHashForUpdate(
            @Param("tokenHash") String tokenHash
    );

    List<EmailVerificationToken>
    findByUserAndUsedAtIsNullAndRevokedAtIsNull(
            User user
    );
}