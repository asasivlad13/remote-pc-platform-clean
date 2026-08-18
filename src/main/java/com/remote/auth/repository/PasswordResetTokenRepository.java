package com.remote.auth.repository;

import com.remote.auth.model.PasswordResetToken;
import com.remote.core.model.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PasswordResetTokenRepository
        extends JpaRepository<PasswordResetToken, Long> {

    /*
     * При фактическом использовании reset-токена строка
     * блокируется до завершения транзакции.
     *
     * Это не позволяет двум параллельным запросам
     * одновременно успешно применить один токен.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select token
            from PasswordResetToken token
            join fetch token.user
            where token.tokenHash = :tokenHash
            """)
    Optional<PasswordResetToken> findByTokenHashForUpdate(
            @Param("tokenHash") String tokenHash
    );

    List<PasswordResetToken>
    findByUserAndUsedAtIsNullAndRevokedAtIsNull(
            User user
    );
}