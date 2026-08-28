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
        name = "education_session_events",
        indexes = {
                @Index(name = "idx_education_session_events_session", columnList = "education_session_id"),
                @Index(name = "idx_education_session_events_actor", columnList = "actor_id"),
                @Index(name = "idx_education_session_events_type", columnList = "type"),
                @Index(name = "idx_education_session_events_created_at", columnList = "created_at")
        }
)
public class EducationSessionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "education_session_id", nullable = false)
    private EducationSession educationSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private EducationSessionEventType type;

    @NotBlank
    @Size(max = 1000)
    @Column(nullable = false, length = 1000)
    private String message;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}