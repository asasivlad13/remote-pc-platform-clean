package com.remote.remoteaccess.model;

import com.remote.core.model.User;
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
import java.util.UUID;

@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(
        name = "remote_sessions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_remote_sessions_session_id",
                        columnNames = "session_id"
                )
        },
        indexes = {
                @Index(
                        name = "idx_remote_sessions_pc_started_at",
                        columnList = "pc_id, started_at"
                ),
                @Index(
                        name = "idx_remote_sessions_user_started_at",
                        columnList = "user_id, started_at"
                ),
                @Index(
                        name = "idx_remote_sessions_ended_at",
                        columnList = "ended_at"
                )
        }
)
public class RemoteSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * Идентификатор именно удалённого сеанса.
     *
     * Не является:
     * - AuthSession id;
     * - WebSocketSession id;
     * - EducationSession id;
     * - SupportSession id.
     */
    @NotNull
    @Column(
            name = "session_id",
            nullable = false,
            updatable = false
    )
    private UUID sessionId;

    /*
     * Удалённый сеанс всегда относится
     * к конкретной установке ПК.
     */
    @NotNull
    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "pc_id",
            nullable = false
    )
    private Pc pc;

    /*
     * Пользователь может временно отсутствовать
     * только для compatibility с текущей legacy-
     * логикой, где допускается "unknown".
     *
     * После ужесточения WebSocket-authentication
     * эту связь можно будет сделать обязательной.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /*
     * Исторический snapshot email.
     *
     * Он не заменяет user_id, а сохраняет значение,
     * существовавшее на момент подключения.
     */
    @Size(max = 254)
    @Column(
            name = "user_email",
            length = 254
    )
    private String userEmail;

    /*
     * Исторический snapshot имени ПК.
     *
     * Если пользователь позже переименует устройство,
     * старая история останется неизменной.
     */
    @NotBlank
    @Size(max = 100)
    @Column(
            name = "pc_name",
            nullable = false,
            length = 100
    )
    private String pcName;

    /*
     * Сценарий подключения:
     * personal, education, support и т.п.
     */
    @NotBlank
    @Size(max = 50)
    @Column(
            name = "profile",
            nullable = false,
            length = 50
    )
    private String profile;

    /*
     * Режим конкретного подключения,
     * например Control.
     */
    @NotBlank
    @Size(max = 50)
    @Column(
            name = "mode",
            nullable = false,
            length = 50
    )
    private String mode = "Control";

    @Size(max = 45)
    @Column(
            name = "client_ip",
            length = 45
    )
    private String clientIp;

    @Size(max = 1000)
    @Column(
            name = "client_info",
            length = 1000
    )
    private String clientInfo;

    @NotNull
    @Column(
            name = "started_at",
            nullable = false,
            updatable = false
    )
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Min(0)
    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Min(0)
    @Column(name = "avg_fps")
    private Double avgFps;

    @Min(0)
    @Column(name = "avg_latency")
    private Double avgLatency;

    @NotNull
    @Min(0)
    @Column(
            name = "files_sent",
            nullable = false
    )
    private Integer filesSent = 0;

    @Size(max = 2000)
    @Column(
            name = "issues",
            length = 2000
    )
    private String issues;

    @PrePersist
    private void onCreate() {
        if (sessionId == null) {
            sessionId = UUID.randomUUID();
        }

        if (startedAt == null) {
            startedAt = Instant.now();
        }

        if (filesSent == null) {
            filesSent = 0;
        }

        if (mode == null
                || mode.isBlank()) {

            mode = "Control";
        }
    }
}