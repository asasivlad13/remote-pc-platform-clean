package com.remote.pc.repository;

import com.remote.core.model.User;
import com.remote.pc.model.Pc;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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
     * device-auth и обычное чтение installationId
     * должны знать владельца установки.
     */
    @EntityGraph(attributePaths = "user")
    Optional<Pc> findByInstallationId(
            UUID installationId
    );

    /*
     * Атомарно создаёт первоначальную запись Pc.
     *
     * PostgreSQL UNIQUE(installation_id) является
     * арбитром при нескольких одновременных
     * legacy bootstrap-запросах.
     *
     * Если другая транзакция уже создала тот же
     * installationId, INSERT не падает с unique
     * violation, а возвращает 0 изменённых строк.
     */
    @Modifying
    @Query(
            value = """
                    INSERT INTO pcs (
                        installation_id,
                        name,
                        device_name,
                        mac_address,
                        os_name,
                        os_version,
                        agent_version,
                        protocol_version,
                        connection_status,
                        power_state,
                        last_seen_at,
                        user_id,
                        created_at,
                        updated_at
                    )
                    VALUES (
                        :installationId,
                        :name,
                        :deviceName,
                        :macAddress,
                        :osName,
                        :osVersion,
                        :agentVersion,
                        :protocolVersion,
                        'ONLINE',
                        'AWAKE',
                        :lastSeenAt,
                        :userId,
                        :createdAt,
                        :updatedAt
                    )
                    ON CONFLICT (installation_id)
                    DO NOTHING
                    """,
            nativeQuery = true
    )
    int insertBootstrapIfAbsent(
            @Param("installationId")
            UUID installationId,

            @Param("name")
            String name,

            @Param("deviceName")
            String deviceName,

            @Param("macAddress")
            String macAddress,

            @Param("osName")
            String osName,

            @Param("osVersion")
            String osVersion,

            @Param("agentVersion")
            String agentVersion,

            @Param("protocolVersion")
            Integer protocolVersion,

            @Param("lastSeenAt")
            Instant lastSeenAt,

            @Param("userId")
            Long userId,

            @Param("createdAt")
            Instant createdAt,

            @Param("updatedAt")
            Instant updatedAt
    );

    /*
     * После атомарного bootstrap INSERT
     * строка установки блокируется до конца
     * транзакции регистрации.
     *
     * Поэтому параллельные REGISTER для одного
     * installationId последовательно проверяют
     * владельца и обновляют metadata.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select pc
            from Pc pc
            where pc.installationId = :installationId
            """)
    Optional<Pc> findByInstallationIdForUpdate(
            @Param("installationId")
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