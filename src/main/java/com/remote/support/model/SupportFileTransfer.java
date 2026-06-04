package com.remote.support.model;

import com.remote.core.model.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(
        name = "support_file_transfers",
        indexes = {
                @Index(name = "idx_support_file_transfers_session", columnList = "support_session_id"),
                @Index(name = "idx_support_file_transfers_sender", columnList = "sender_id"),
                @Index(name = "idx_support_file_transfers_recipient", columnList = "recipient_id"),
                @Index(name = "idx_support_file_transfers_status", columnList = "status"),
                @Index(name = "idx_support_file_transfers_created_at", columnList = "created_at")
        }
)
public class SupportFileTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "support_session_id", nullable = false)
    private SupportSession supportSession;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @NotBlank
    @Size(max = 255)
    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Size(max = 100)
    @Column(name = "content_type", length = 100)
    private String contentType;

    @NotNull
    @Min(0)
    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @NotNull
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "file_data", nullable = false, columnDefinition = "bytea")
    private byte[] fileData;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SupportFileTransferStatus status = SupportFileTransferStatus.PENDING;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    public void accept() {
        this.status = SupportFileTransferStatus.ACCEPTED;
        this.decidedAt = LocalDateTime.now();
    }

    public void reject() {
        this.status = SupportFileTransferStatus.REJECTED;
        this.decidedAt = LocalDateTime.now();
    }
}