package com.remote.pc.repository;

import com.remote.core.model.User;
import com.remote.pc.model.Pc;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PcRepository
        extends JpaRepository<Pc, Long> {

    List<Pc> findByUser(
            User user
    );

    /*
     * User загружается вместе с Pc, потому что
     * device-auth и legacy-регистрация агента
     * должны знать владельца installationId.
     */
    @EntityGraph(attributePaths = "user")
    Optional<Pc> findByInstallationId(
            UUID installationId
    );

    /*
     * Используется security-операциями над устройством.
     *
     * В частности, первоначальная выдача credential
     * блокирует строку Pc, потому что строки
     * device_credentials в этот момент ещё может не быть.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select pc
            from Pc pc
            where pc.id = :pcId
            """)
    Optional<Pc> findByIdForUpdate(
            @Param("pcId")
            Long pcId
    );
}