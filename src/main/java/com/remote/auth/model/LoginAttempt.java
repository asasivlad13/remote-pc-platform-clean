package com.remote.auth.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "login_attempts",
        indexes = {
                @Index(name = "idx_login_attempts_ip_address", columnList = "ip_address")
        }
)
public class LoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(name = "ip_address", nullable = false, length = 45, unique = true)
    private String ipAddress;

    @Min(0)
    @Column(nullable = false)
    private int attempts;

    @Column(name = "block_until")
    private LocalDateTime blockUntil;

    @Column(name = "last_attempt", nullable = false)
    private LocalDateTime lastAttempt;

    public LoginAttempt(String ipAddress) {
        this.ipAddress = ipAddress;
        this.attempts = 0;
        this.lastAttempt = LocalDateTime.now();
    }
}