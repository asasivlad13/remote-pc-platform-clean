package com.remote.auth.repository;

import com.remote.auth.model.DeviceCredential;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DeviceCredentialRepository
        extends JpaRepository<DeviceCredential, Long> {

    Optional<DeviceCredential> findByPcId(
            Long pcId
    );

    /*
     * Rotation, revocation и успешная device-auth
     * изменяют одну и ту же credential-запись.
     *
     * Поэтому такие операции должны сериализоваться
     * блокировкой строки.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select credential
            from DeviceCredential credential
            where credential.pc.id = :pcId
            """)
    Optional<DeviceCredential> findByPcIdForUpdate(
            @Param("pcId")
            Long pcId
    );
}