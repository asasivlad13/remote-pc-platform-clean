package com.remote.pc.repository;

import com.remote.core.model.User;
import com.remote.pc.model.Pc;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PcRepository
        extends JpaRepository<Pc, Long> {

    List<Pc> findByUser(User user);

    /*
     * User загружается вместе с Pc, потому что регистрация
     * агента проверяет владельца installationId.
     */
    @EntityGraph(attributePaths = "user")
    Optional<Pc> findByInstallationId(
            UUID installationId
    );
}