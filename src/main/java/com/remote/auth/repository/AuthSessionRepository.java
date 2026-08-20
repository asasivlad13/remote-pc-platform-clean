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
     * Блокируем именно строку auth_sessions.
     *
     * User здесь намеренно не join-fetch'ится:
     * security-flow использует единый порядок
     * блокировок и не должен дополнительно
     * блокировать users через этот запрос.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s
            from AuthSession s
            where s.id = :sessionId
            """)
    Optional<AuthSession> findByIdForUpdate(
            @Param("sessionId") UUID sessionId
    );

    List<AuthSession>
    findByUserAndRevokedAtIsNullOrderByCreatedAtDesc(
            User user
    );
}