package com.remote.pc.model;

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
        name = "pcs",
        indexes = {
                @Index(
                        name = "idx_pcs_user_id",
                        columnList = "user_id"
                ),
                @Index(
                        name = "idx_pcs_connection_status",
                        columnList = "connection_status"
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
     */
    @NotBlank
    @Size(max = 100)
    @Column(
            nullable = false,
            length = 100
    )
    private String name;

    /*
     * Системное имя устройства.
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
     * MAC является необязательной характеристикой
     * сетевого интерфейса, а не идентификатором
     * установки агента.
     *
     * Идентичность установки определяется
     * installationId.
     */
    @Size(max = 50)
    @Column(
            name = "mac_address",
            nullable = true,
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

    /*
     * Состояние соединения агента с backend.
     *
     * Не связано с программным режимом сна.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(
            name = "connection_status",
            nullable = false,
            length = 30
    )
    private PcConnectionStatus connectionStatus =
            PcConnectionStatus.OFFLINE;

    /*
     * Локальное состояние питания/режима работы ПК.
     *
     * Сейчас здесь отражается именно реализованный
     * программный SOFT_SLEEP.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(
            name = "power_state",
            nullable = false,
            length = 30
    )
    private PcPowerState powerState =
            PcPowerState.AWAKE;

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
     * будут вынесены в отдельное runtime-состояние.
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

    /*
     * Временная compatibility-проекция для существующего API.
     *
     * Поля status в таблице pcs больше нет.
     */
    @Transient
    public PcStatus getStatus() {
        if (connectionStatus != PcConnectionStatus.ONLINE) {
            return PcStatus.OFFLINE;
        }

        if (powerState == PcPowerState.SOFT_SLEEP) {
            return PcStatus.SLEEP;
        }

        return PcStatus.ONLINE;
    }

    /*
     * Временный compatibility-setter.
     *
     * Новый backend-код должен использовать
     * connectionStatus и powerState напрямую.
     */
    @Transient
    public void setStatus(PcStatus status) {
        if (status == null) {
            return;
        }

        switch (status) {
            case ONLINE -> {
                connectionStatus =
                        PcConnectionStatus.ONLINE;

                powerState =
                        PcPowerState.AWAKE;
            }

            case OFFLINE ->
                    connectionStatus =
                            PcConnectionStatus.OFFLINE;

            case SLEEP ->
                    powerState =
                            PcPowerState.SOFT_SLEEP;
        }
    }

    @PrePersist
    private void onCreate() {
        Instant now =
                Instant.now();

        if (createdAt == null) {
            createdAt = now;
        }

        if (updatedAt == null) {
            updatedAt = now;
        }

        if (connectionStatus == null) {
            connectionStatus =
                    PcConnectionStatus.OFFLINE;
        }

        if (powerState == null) {
            powerState =
                    PcPowerState.AWAKE;
        }
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt =
                Instant.now();
    }
}
