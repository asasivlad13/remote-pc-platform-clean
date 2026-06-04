package com.remote.education.model;

import com.remote.core.model.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
        name = "education_chat_messages",
        indexes = {
                @Index(name = "idx_education_chat_messages_session", columnList = "education_session_id"),
                @Index(name = "idx_education_chat_messages_sender", columnList = "sender_id"),
                @Index(name = "idx_education_chat_messages_recipient", columnList = "recipient_id"),
                @Index(name = "idx_education_chat_messages_created_at", columnList = "created_at")
        }
)
public class EducationChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "education_session_id", nullable = false)
    private EducationSession educationSession;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id")
    private User recipient;

    @NotBlank
    @Size(max = 2000)
    @Column(nullable = false, length = 2000)
    private String message;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}