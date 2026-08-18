package com.remote.auth.model;

import com.remote.core.model.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(
        name = "auth_sessions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_auth_sessions_refresh_token_hash",
                        columnNames = "refresh_token_hash"
                )
        },
        indexes = {
                @Index(
                        name = "idx_auth_sessions_user_revoked_at",
                        columnList = "user_id, revoked_at"
                ),
                @Index(
                        name = "idx_auth_sessions_expires_at",
                        columnList = "expires_at"
                )
        }
)
public class AuthSession {

    /*
     * UUID является публичным идентификатором сессии.
     *
     * Позже он будет частью refresh token,
     * но сам по себе не является секретом.
     */
    @Id
    @NotNull
    @Column(
            nullable = false,
            updatable = false
    )
    private UUID id;

    @NotNull
    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "user_id",
            nullable = false
    )
    private User user;

    /*
     * В БД хранится только SHA-256 hash
     * текущего refresh token.
     */
    @NotBlank
    @Size(max = 64)
    @Column(
            name = "refresh_token_hash",
            nullable = false,
            length = 64
    )
    private String refreshTokenHash;

    /*
     * Версия увеличивается при каждой rotation.
     *
     * Позволяет определить устаревший refresh token
     * без хранения всей истории токенов.
     */
    @NotNull
    @Min(1)
    @Column(
            name = "refresh_token_version",
            nullable = false
    )
    private Integer refreshTokenVersion = 1;

    /*
     * IP и User-Agent являются метаданными сессии.
     * Они не участвуют в аутентификации.
     */
    @Size(max = 45)
    @Column(
            name = "ip_address",
            length = 45
    )
    private String ipAddress;

    @Size(max = 512)
    @Column(
            name = "user_agent",
            length = 512
    )
    private String userAgent;

    @NotNull
    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    /*
     * Последнее успешное использование refresh token.
     *
     * До первой rotation может быть null.
     */
    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    /*
     * Абсолютный срок жизни refresh-сессии.
     *
     * Rotation refresh token не должна бесконечно
     * продлевать этот срок.
     */
    @NotNull
    @Column(
            name = "expires_at",
            nullable = false
    )
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "revocation_reason",
            length = 40
    )
    private AuthSessionRevokeReason revocationReason;

    @PrePersist
    private void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }

        if (createdAt == null) {
            createdAt = Instant.now();
        }

        if (refreshTokenVersion == null) {
            refreshTokenVersion = 1;
        }
    }
}