package com.remote.auth.repository;

import com.remote.auth.model.AuthSession;
import com.remote.core.model.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthSessionRepository
        extends JpaRepository<AuthSession, UUID> {

    /*
     * Refresh-token rotation выполняется
     * под блокировкой строки сессии.
     *
     * Поэтому два параллельных refresh-запроса
     * не смогут одновременно успешно
     * использовать одну версию токена.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s
            from AuthSession s
            join fetch s.user
            where s.id = :sessionId
            """)
    Optional<AuthSession> findByIdForUpdate(
            @Param("sessionId") UUID sessionId
    );

    /*
     * Используется для logout-all,
     * смены/сброса пароля и security revocation.
     */
    List<AuthSession>
    findByUserAndRevokedAtIsNullOrderByCreatedAtDesc(
            User user
    );
}