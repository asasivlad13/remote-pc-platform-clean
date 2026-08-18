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

import java.time.LocalDateTime;
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
                        name = "idx_pcs_last_connection",
                        columnList = "last_connection"
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

    /*
     * Постоянный идентификатор установки агента.
     *
     * Именно installationId определяет идентичность
     * зарегистрированного устройства.
     */
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
     * MAC является характеристикой устройства.
     *
     * Он может изменяться и не является уникальным
     * идентификатором записи Pc.
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

    @Column(name = "last_connection")
    private LocalDateTime lastConnection;

    @Min(1)
    @Column(name = "screen_width")
    private Integer screenWidth;

    @Min(1)
    @Column(name = "screen_height")
    private Integer screenHeight;

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
}