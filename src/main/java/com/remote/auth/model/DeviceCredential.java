package com.remote.auth.model;

import com.remote.pc.model.Pc;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "device_credentials",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_device_credentials_pc_id",
                        columnNames = "pc_id"
                ),
                @UniqueConstraint(
                        name = "uk_device_credentials_hash",
                        columnNames = "credential_hash"
                )
        }
)
public class DeviceCredential {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    private Long id;

    /*
     * Для одной установки ПК существует
     * только один текущий credential.
     *
     * installationId остаётся в таблице pcs
     * и не дублируется здесь.
     */
    @NotNull
    @OneToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "pc_id",
            nullable = false
    )
    private Pc pc;

    /*
     * В БД хранится только SHA-256 hash
     * текущего device credential.
     *
     * Raw credential существует только
     * в момент выдачи сервером и на агенте.
     */
    @NotBlank
    @Size(
            min = 64,
            max = 64
    )
    @Column(
            name = "credential_hash",
            nullable = false,
            length = 64
    )
    private String credentialHash;

    /*
     * Версия увеличивается при rotation.
     *
     * Она не является секретом.
     */
    @NotNull
    @Min(1)
    @Column(
            name = "credential_version",
            nullable = false
    )
    private Integer credentialVersion = 1;

    @NotNull
    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    /*
     * Время последней успешной rotation.
     *
     * Для первоначально выданного credential
     * остаётся null.
     */
    @Column(name = "rotated_at")
    private Instant rotatedAt;

    /*
     * Последняя успешная аутентификация агента
     * этим credential.
     */
    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    /*
     * revokedAt != null означает, что credential
     * больше нельзя использовать.
     */
    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "revocation_reason",
            length = 40
    )
    private DeviceCredentialRevokeReason revocationReason;

    @PrePersist
    private void onCreate() {
        if (createdAt == null) {
            createdAt =
                    Instant.now();
        }

        if (credentialVersion == null) {
            credentialVersion =
                    1;
        }
    }
}