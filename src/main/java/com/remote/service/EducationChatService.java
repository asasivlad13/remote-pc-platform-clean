package com.remote.service;

import com.remote.model.EducationChatMessage;
import com.remote.model.EducationSession;
import com.remote.model.User;
import com.remote.repository.EducationChatMessageRepository;
import com.remote.repository.EducationSessionRepository;
import com.remote.repository.UserRepository;
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
    private final UserRepository userRepository;

    public EducationChatService(EducationChatMessageRepository chatRepository,
                                EducationSessionRepository sessionRepository,
                                UserRepository userRepository) {
        this.chatRepository = chatRepository;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getMessages(String sessionCode) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        EducationSession session = sessionRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> new IllegalArgumentException("Учебная сессия не найдена"));

        boolean isTeacher = session.getTeacher().getId().equals(currentUser.getId());

        return chatRepository.findByEducationSessionOrderByCreatedAtAsc(session)
                .stream()
                .filter(message -> isVisibleForUser(message, currentUser, isTeacher))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public Map<String, Object> sendMessage(String sessionCode, String text, Long recipientId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        User sender = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        EducationSession session = sessionRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> new IllegalArgumentException("Учебная сессия не найдена"));

        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Сообщение не может быть пустым");
        }

        if (text.length() > 2000) {
            throw new IllegalArgumentException("Сообщение слишком длинное");
        }

        boolean isTeacher = session.getTeacher().getId().equals(sender.getId());

        User recipient = null;

        if (isTeacher) {
            if (recipientId != null) {
                recipient = userRepository.findById(recipientId)
                        .orElseThrow(() -> new IllegalArgumentException("Получатель не найден"));
            }
        } else {
            recipient = session.getTeacher();
        }

        EducationChatMessage message = new EducationChatMessage();
        message.setEducationSession(session);
        message.setSender(sender);
        message.setRecipient(recipient);
        message.setMessage(text.trim());

        EducationChatMessage saved = chatRepository.save(message);

        saved.getSender().getUsername();
        if (saved.getRecipient() != null) {
            saved.getRecipient().getUsername();
        }

        return toResponse(saved);
    }

    private boolean isVisibleForUser(EducationChatMessage message, User currentUser, boolean isTeacher) {
        if (isTeacher) {
            return true;
        }

        if (message.getRecipient() == null) {
            return true;
        }

        if (message.getSender().getId().equals(currentUser.getId())) {
            return true;
        }

        return message.getRecipient().getId().equals(currentUser.getId());
    }

    private Map<String, Object> toResponse(EducationChatMessage message) {
        Map<String, Object> response = new LinkedHashMap<>();

        response.put("id", message.getId());
        response.put("senderId", message.getSender().getId());
        response.put("senderUsername", message.getSender().getUsername());
        response.put("recipientId", message.getRecipient() != null ? message.getRecipient().getId() : null);
        response.put("recipientUsername", message.getRecipient() != null ? message.getRecipient().getUsername() : null);
        response.put("message", message.getMessage());
        response.put("createdAt", message.getCreatedAt());

        return response;
    }
}