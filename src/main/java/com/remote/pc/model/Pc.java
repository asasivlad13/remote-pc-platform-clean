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

    @NotBlank
    @Size(max = 100)
    @Column(
            nullable = false,
            length = 100
    )
    private String name;

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

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 30
    )
    private PcStatus status =
            PcStatus.OFFLINE;

    /*
     * Последний момент, когда backend получил
     * регистрацию или heartbeat от агента.
     *
     * Instant позволяет хранить абсолютный момент времени
     * независимо от часового пояса сервера.
     */
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
     * с текущими Education/Support-сценариями.
     * Позже параметры активного видеосеанса
     * будут перенесены в модель remote session.
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

    public Pc(
            String name,
            String macAddress,
            User user
    ) {
        this.name = name;
        this.macAddress = macAddress;
        this.user = user;
        this.status = PcStatus.OFFLINE;
    }

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