package com.remote.support.model;

import com.remote.core.model.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(
        name = "support_chat_messages",
        indexes = {
                @Index(name = "idx_support_chat_messages_session", columnList = "support_session_id"),
                @Index(name = "idx_support_chat_messages_sender", columnList = "sender_id"),
                @Index(name = "idx_support_chat_messages_created_at", columnList = "created_at")
        }
)
public class SupportChatMessage {

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

    @NotBlank
    @Size(max = 2000)
    @Column(nullable = false, length = 2000)
    private String message;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}