package com.remote.remoteaccess.repository;

import com.remote.remoteaccess.model.RemoteSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RemoteSessionRepository
        extends JpaRepository<RemoteSession, Long> {

    Optional<RemoteSession> findBySessionId(
            UUID sessionId
    );

    List<RemoteSession>
    findByUserIdOrderByStartedAtDesc(
            Long userId
    );
}