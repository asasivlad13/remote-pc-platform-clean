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
     * Предварительное чтение нужно только для определения
     * владельца token перед получением user-level lock.
     *
     * После блокировки пользователя token обязательно
     * перечитывается уже через PESSIMISTIC_WRITE.
     */
    @Query("""
            select token
            from PasswordResetToken token
            join fetch token.user
            where token.tokenHash = :tokenHash
            """)
    Optional<PasswordResetToken> findByTokenHash(
            @Param("tokenHash") String tokenHash
    );

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