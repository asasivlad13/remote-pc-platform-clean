package com.remote.education.service;

import com.remote.common.crypto.SharedCryptoService;
import com.remote.core.model.User;
import com.remote.core.repository.UserRepository;
import com.remote.education.model.EducationChatMessage;
import com.remote.education.model.EducationSession;
import com.remote.education.repository.EducationChatMessageRepository;
import com.remote.education.repository.EducationSessionParticipantRepository;
import com.remote.education.repository.EducationSessionRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class EducationChatService {

    private final EducationChatMessageRepository chatRepository;
    private final EducationSessionRepository sessionRepository;
    private final EducationSessionParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final SharedCryptoService cryptoService;

    public EducationChatService(EducationChatMessageRepository chatRepository,
                                EducationSessionRepository sessionRepository,
                                EducationSessionParticipantRepository participantRepository,
                                UserRepository userRepository,
                                SharedCryptoService cryptoService) {
        this.chatRepository = chatRepository;
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.userRepository = userRepository;
        this.cryptoService = cryptoService;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getMessages(String sessionCode) {
        String username = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Пользователь не найден"
                ));

        EducationSession session = sessionRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Учебная сессия не найдена"
                ));

        boolean isTeacher = session.getTeacher()
                .getId()
                .equals(currentUser.getId());

        return chatRepository
                .findByEducationSessionOrderByCreatedAtAsc(session)
                .stream()
                .filter(message ->
                        isVisibleForUser(
                                message,
                                currentUser,
                                isTeacher
                        )
                )
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public Map<String, Object> sendMessage(String sessionCode,
                                           String text,
                                           Long recipientId) {
        String username = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        User sender = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Пользователь не найден"
                ));

        EducationSession session = sessionRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Учебная сессия не найдена"
                ));

        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(
                    "Сообщение не может быть пустым"
            );
        }

        if (text.length() > 2000) {
            throw new IllegalArgumentException(
                    "Сообщение слишком длинное"
            );
        }

        boolean isTeacher = session.getTeacher()
                .getId()
                .equals(sender.getId());

        User recipient = null;

        if (isTeacher) {
            if (recipientId != null) {
                recipient = userRepository.findById(recipientId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Получатель не найден"
                        ));
            }
        } else {
            recipient = session.getTeacher();
        }

        String encryptedText =
                cryptoService.encryptText(text.trim());

        EducationChatMessage message =
                new EducationChatMessage();

        message.setEducationSession(session);
        message.setSender(sender);
        message.setRecipient(recipient);
        message.setMessage(encryptedText);

        EducationChatMessage saved =
                chatRepository.save(message);

        /*
         * Принудительно обращаемся к lazy-полям внутри транзакции,
         * чтобы при формировании ответа не было проблем
         * с загрузкой sender/recipient.
         */
        saved.getSender().getUsername();

        if (saved.getRecipient() != null) {
            saved.getRecipient().getUsername();
        }

        return toResponse(saved);
    }

    private boolean isVisibleForUser(EducationChatMessage message,
                                     User currentUser,
                                     boolean isTeacher) {
        if (isTeacher) {
            return true;
        }

        if (message.getRecipient() == null) {
            return true;
        }

        if (message.getSender()
                .getId()
                .equals(currentUser.getId())) {
            return true;
        }

        return message.getRecipient()
                .getId()
                .equals(currentUser.getId());
    }

    private Map<String, Object> toResponse(EducationChatMessage message) {
        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put(
                "id",
                message.getId()
        );

        response.put(
                "senderId",
                message.getSender().getId()
        );

        response.put(
                "senderUsername",
                message.getSender().getUsername()
        );

        response.put(
                "senderDisplayName",
                resolveUserDisplayName(
                        message.getEducationSession(),
                        message.getSender()
                )
        );

        response.put(
                "recipientId",
                message.getRecipient() != null
                        ? message.getRecipient().getId()
                        : null
        );

        response.put(
                "recipientUsername",
                message.getRecipient() != null
                        ? message.getRecipient().getUsername()
                        : null
        );

        response.put(
                "recipientDisplayName",
                message.getRecipient() != null
                        ? resolveUserDisplayName(
                        message.getEducationSession(),
                        message.getRecipient()
                )
                        : null
        );

        response.put(
                "message",
                decryptMessageSafe(message.getMessage())
        );

        response.put(
                "createdAt",
                message.getCreatedAt()
        );

        return response;
    }

    private String resolveUserDisplayName(EducationSession session,
                                          User user) {
        if (session == null || user == null) {
            return null;
        }

        if (session.getTeacher() != null
                && session.getTeacher()
                .getId()
                .equals(user.getId())) {

            return resolveDisplayName(
                    session.getTeacherDisplayName(),
                    user.getUsername()
            );
        }

        return participantRepository
                .findByEducationSessionAndStudent(session, user)
                .map(participant ->
                        resolveDisplayName(
                                participant.getDisplayName(),
                                user.getUsername()
                        )
                )
                .orElse(user.getUsername());
    }

    private String resolveDisplayName(String displayName,
                                      String fallbackUsername) {
        if (displayName != null && !displayName.isBlank()) {
            return displayName.trim();
        }

        if (fallbackUsername != null
                && !fallbackUsername.isBlank()) {
            return fallbackUsername;
        }

        return "Пользователь";
    }

    private String decryptMessageSafe(String value) {
        if (value == null) {
            return null;
        }

        try {
            return cryptoService.decryptText(value);
        } catch (IllegalStateException e) {
            return value;
        }
    }
}