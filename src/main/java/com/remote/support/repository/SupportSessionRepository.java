package com.remote.support.repository;

import com.remote.support.model.SupportSession;
import com.remote.support.model.SupportSessionStatus;
import com.remote.core.model.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupportSessionRepository extends JpaRepository<SupportSession, Long> {

    @EntityGraph(attributePaths = {"operator", "client", "clientPc"})
    Optional<SupportSession> findBySessionCode(String sessionCode);

    boolean existsBySessionCode(String sessionCode);

    @EntityGraph(attributePaths = {"operator", "client", "clientPc"})
    List<SupportSession> findByOperatorAndStatusOrderByCreatedAtDesc(
            User operator,
            SupportSessionStatus status
    );

    @EntityGraph(attributePaths = {"operator", "client", "clientPc"})
    Optional<SupportSession> findFirstByOperatorAndStatusOrderByCreatedAtDesc(
            User operator,
            SupportSessionStatus status
    );

    @EntityGraph(attributePaths = {"operator", "client", "clientPc"})
    Optional<SupportSession> findFirstByClientAndStatusOrderByCreatedAtDesc(
            User client,
            SupportSessionStatus status
    );
}
