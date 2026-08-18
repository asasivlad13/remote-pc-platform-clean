package com.remote.auth.model;

import com.remote.core.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(
        name = "password_reset_tokens",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_password_reset_tokens_token_hash",
                        columnNames = "token_hash"
                )
        },
        indexes = {
                @Index(
                        name = "idx_password_reset_tokens_user_id",
                        columnList = "user_id"
                ),
                @Index(
                        name = "idx_password_reset_tokens_expires_at",
                        columnList = "expires_at"
                )
        }
)
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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
     * В БД хранится только SHA-256 hash reset-токена.
     * Сам секретный токен получает пользователь.
     */
    @Column(
            name = "token_hash",
            nullable = false,
            length = 64
    )
    private String tokenHash;

    @Column(
            name = "expires_at",
            nullable = false
    )
    private Instant expiresAt;

    /*
     * Момент успешного применения токена.
     * Использованный токен повторно применяться не может.
     */
    @Column(name = "used_at")
    private Instant usedAt;

    /*
     * Позволяет инвалидировать старый токен,
     * например при повторном запросе восстановления.
     */
    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @PrePersist
    private void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}