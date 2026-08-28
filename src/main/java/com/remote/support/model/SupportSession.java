package com.remote.support.model;

import com.remote.core.model.User;
import com.remote.pc.model.Pc;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(
        name = "support_sessions",
        indexes = {
                @Index(name = "idx_support_sessions_session_code", columnList = "session_code"),
                @Index(name = "idx_support_sessions_operator", columnList = "operator_id"),
                @Index(name = "idx_support_sessions_client", columnList = "client_id"),
                @Index(name = "idx_support_sessions_client_pc", columnList = "client_pc_id"),
                @Index(name = "idx_support_sessions_status", columnList = "status"),
                @Index(name = "idx_support_sessions_created_at", columnList = "created_at")
        }
)
public class SupportSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Pattern(regexp = "\\d{6}")
    @Column(name = "session_code", nullable = false, unique = true, length = 6)
    private String sessionCode;

    @NotBlank
    @Size(max = 150)
    @Column(nullable = false, length = 150)
    private String title;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operator_id", nullable = false)
    private User operator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private User client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_pc_id")
    private Pc clientPc;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SupportSessionStatus status = SupportSessionStatus.WAITING_CLIENT;

    @Column(name = "control_requested", nullable = false)
    private boolean controlRequested = false;

    @Column(name = "control_allowed", nullable = false)
    private boolean controlAllowed = false;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "control_requested_at")
    private Instant controlRequestedAt;

    @Column(name = "control_allowed_at")
    private Instant controlAllowedAt;

    public void finish() {
        this.status = SupportSessionStatus.FINISHED;
        this.finishedAt = Instant.now();
        this.controlRequested = false;
        this.controlAllowed = false;
        this.controlRequestedAt = null;
        this.controlAllowedAt = null;
    }

    public void cancel() {
        this.status = SupportSessionStatus.CANCELLED;
        this.finishedAt = Instant.now();
        this.controlRequested = false;
        this.controlAllowed = false;
        this.controlRequestedAt = null;
        this.controlAllowedAt = null;
    }
}