package com.remote.history.model;

import com.remote.pc.model.Pc;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(
        name = "connection_logs",
        indexes = {
                @Index(name = "idx_connection_logs_session_id", columnList = "session_id"),
                @Index(name = "idx_connection_logs_username", columnList = "username"),
                @Index(name = "idx_connection_logs_pc_name", columnList = "pc_name"),
                @Index(name = "idx_connection_logs_pc_id", columnList = "pc_id"),
                @Index(name = "idx_connection_logs_timestamp", columnList = "timestamp"),
                @Index(name = "idx_connection_logs_disconnected_at", columnList = "disconnected_at")
        }
)
public class ConnectionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @NotBlank
    @Size(max = 50)
    @Column(nullable = false, length = 50)
    private String username;

    @NotBlank
    @Size(max = 100)
    @Column(name = "pc_name", nullable = false, length = 100)
    private String pcName;

    @Size(max = 50)
    @Column(length = 50)
    private String action;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "disconnected_at")
    private LocalDateTime disconnectedAt;

    @Min(0)
    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Size(max = 45)
    @Column(name = "client_ip", length = 45)
    private String clientIp;

    @Size(max = 1000)
    @Column(name = "client_info", length = 1000)
    private String clientInfo;

    @Size(max = 50)
    @Column(length = 50)
    private String mode;

    @Min(0)
    @Column(name = "avg_fps")
    private Double avgFps;

    @Min(0)
    @Column(name = "avg_latency")
    private Double avgLatency;

    @Min(0)
    @Column(name = "files_sent")
    private Integer filesSent;

    @Size(max = 2000)
    @Column(length = 2000)
    private String issues;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pc_id")
    private Pc pc;

    public ConnectionLog() {
    }

    public ConnectionLog(String username, String pcName, String action, String clientIp) {
        this.sessionId = UUID.randomUUID();
        this.username = username;
        this.pcName = pcName;
        this.action = action;
        this.clientIp = clientIp;
        this.timestamp = LocalDateTime.now();
        this.mode = "Control";
        this.avgFps = 0.0;
        this.avgLatency = 0.0;
        this.filesSent = 0;
        this.issues = "";
    }
}