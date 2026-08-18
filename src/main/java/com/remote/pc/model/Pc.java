package com.remote.pc.model;

import com.remote.core.model.User;
import com.remote.history.model.ConnectionLog;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(
        name = "pcs",
        indexes = {
                @Index(
                        name = "idx_pcs_mac_address",
                        columnList = "mac_address"
                ),
                @Index(
                        name = "idx_pcs_user_id",
                        columnList = "user_id"
                ),
                @Index(
                        name = "idx_pcs_status",
                        columnList = "status"
                ),
                @Index(
                        name = "idx_pcs_last_seen_at",
                        columnList = "last_seen_at"
                )
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_pcs_installation_id",
                        columnNames = "installation_id"
                )
        }
)
public class Pc {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(
            name = "installation_id",
            nullable = false
    )
    private UUID installationId;

    /*
     * Пользовательское имя ПК внутри платформы.
     * Например: "Домашний компьютер".
     */
    @NotBlank
    @Size(max = 100)
    @Column(
            nullable = false,
            length = 100
    )
    private String name;

    /*
     * Имя устройства, которое сообщает операционная система.
     * Например: DESKTOP-ABC123.
     */
    @NotBlank
    @Size(max = 255)
    @Column(
            name = "device_name",
            nullable = false,
            length = 255
    )
    private String deviceName;

    /*
     * MAC является характеристикой устройства,
     * а не идентификатором установки.
     */
    @NotBlank
    @Size(max = 50)
    @Column(
            name = "mac_address",
            nullable = false,
            length = 50
    )
    private String macAddress;

    @NotBlank
    @Size(max = 100)
    @Column(
            name = "os_name",
            nullable = false,
            length = 100
    )
    private String osName;

    @NotBlank
    @Size(max = 100)
    @Column(
            name = "os_version",
            nullable = false,
            length = 100
    )
    private String osVersion;

    @NotBlank
    @Size(max = 50)
    @Column(
            name = "agent_version",
            nullable = false,
            length = 50
    )
    private String agentVersion;

    @NotNull
    @Min(1)
    @Column(
            name = "protocol_version",
            nullable = false
    )
    private Integer protocolVersion;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 30
    )
    private PcStatus status =
            PcStatus.OFFLINE;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Min(1)
    @Column(name = "screen_width")
    private Integer screenWidth;

    @Min(1)
    @Column(name = "screen_height")
    private Integer screenHeight;

    /*
     * Пока остаются в Pc для совместимости
     * с Education/Support.
     *
     * Позже параметры активного видеосеанса
     * будут вынесены в remote session.
     */
    @Size(max = 500)
    @Column(
            name = "webrtc_url",
            length = 500
    )
    private String webrtcUrl;

    @Size(max = 100)
    @Column(
            name = "stream_name",
            length = 100
    )
    private String streamName;

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

    @OneToMany(
            mappedBy = "pc",
            cascade = CascadeType.ALL
    )
    private List<ConnectionLog> connectionLogs;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @Column(
            name = "updated_at",
            nullable = false
    )
    private Instant updatedAt;

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();

        if (createdAt == null) {
            createdAt = now;
        }

        if (updatedAt == null) {
            updatedAt = now;
        }

        if (status == null) {
            status = PcStatus.OFFLINE;
        }
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = Instant.now();
    }
}