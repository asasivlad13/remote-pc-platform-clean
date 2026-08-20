package com.remote.auth.model;

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
        name = "login_attempts",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_login_attempts_ip_address",
                        columnNames = "ip_address"
                )
        }
)
public class LoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 45)
    @Column(
            name = "ip_address",
            nullable = false,
            length = 45
    )
    private String ipAddress;

    @Min(0)
    @Column(
            name = "attempts",
            nullable = false
    )
    private int attempts;

    @Column(name = "block_until")
    private Instant blockUntil;

    @NotNull
    @Column(
            name = "last_attempt",
            nullable = false
    )
    private Instant lastAttempt;

    public LoginAttempt(
            String ipAddress
    ) {
        this.ipAddress =
                ipAddress;

        this.attempts =
                0;

        this.lastAttempt =
                Instant.now();
    }

    @PrePersist
    private void onCreate() {
        if (lastAttempt == null) {
            lastAttempt =
                    Instant.now();
        }
    }
}