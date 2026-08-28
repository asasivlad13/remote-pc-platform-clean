package com.remote.education.model;

import com.remote.core.model.User;
import jakarta.persistence.*;
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
        name = "education_session_participants",
        indexes = {
                @Index(name = "idx_education_participants_session", columnList = "education_session_id"),
                @Index(name = "idx_education_participants_student", columnList = "student_id"),
                @Index(name = "idx_education_participants_status", columnList = "status"),
                @Index(name = "idx_education_participants_joined_at", columnList = "joined_at"),
                @Index(name = "idx_education_participants_last_activity", columnList = "last_activity_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_education_participants_session_student",
                        columnNames = {"education_session_id", "student_id"}
                )
        }
)
public class EducationSessionParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "education_session_id", nullable = false)
    private EducationSession educationSession;

    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @NotBlank
    @Size(max = 100)
    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EducationParticipantStatus status = EducationParticipantStatus.WAITING;

    @NotNull
    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt = Instant.now();

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "control_requested", nullable = false)
    private boolean controlRequested = false;

    @Column(name = "has_control", nullable = false)
    private boolean hasControl = false;

    @Column(name = "last_activity_at")
    private Instant lastActivityAt;

    @Column(name = "control_requested_at")
    private Instant controlRequestedAt;

    @Column(name = "control_granted_at")
    private Instant controlGrantedAt;

    @Column(name = "screen_share_requested", nullable = false)
    private boolean screenShareRequested = false;

    @Column(name = "screen_share_active", nullable = false)
    private boolean screenShareActive = false;

    @Column(name = "screen_share_requested_at")
    private Instant screenShareRequestedAt;

    @Column(name = "screen_share_started_at")
    private Instant screenShareStartedAt;
}