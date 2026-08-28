package com.remote.education.model;

import com.remote.core.model.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(
        name = "education_file_transfers",
        indexes = {
                @Index(name = "idx_education_file_transfers_session", columnList = "education_session_id"),
                @Index(name = "idx_education_file_transfers_sender", columnList = "sender_id"),
                @Index(name = "idx_education_file_transfers_recipient", columnList = "recipient_id"),
                @Index(name = "idx_education_file_transfers_status", columnList = "status"),
                @Index(name = "idx_education_file_transfers_created_at", columnList = "created_at")
        }
)
public class EducationFileTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "education_session_id", nullable = false)
    private EducationSession educationSession;

    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id")
    private User recipient;

    @NotBlank
    @Size(max = 255)
    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @NotBlank
    @Size(max = 300)
    @Column(name = "stored_filename", nullable = false, length = 300)
    private String storedFilename;

    @Size(max = 100)
    @Column(name = "content_type", length = 100)
    private String contentType;

    @NotNull
    @Min(0)
    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EducationFileTransferStatus status = EducationFileTransferStatus.AVAILABLE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}