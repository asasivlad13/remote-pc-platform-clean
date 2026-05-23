package com.remote.service;

import com.remote.model.SupportChatMessage;
import com.remote.model.SupportSession;
import com.remote.model.SupportSessionStatus;
import com.remote.model.User;
import com.remote.repository.SupportChatMessageRepository;
import com.remote.repository.SupportSessionRepository;
import com.remote.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SupportChatService {

    private final SupportChatMessageRepository supportChatMessageRepository;
    private final SupportSessionRepository supportSessionRepository;
    private final UserRepository userRepository;

    public SupportChatService(SupportChatMessageRepository supportChatMessageRepository,
                              SupportSessionRepository supportSessionRepository,
                              UserRepository userRepository) {
        this.supportChatMessageRepository = supportChatMessageRepository;
        this.supportSessionRepository = supportSessionRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getMessages(String username, String sessionCode) {
        User currentUser = findUser(username);
        SupportSession session = findSession(sessionCode);
        checkActiveParticipant(session, currentUser);

        return supportChatMessageRepository.findBySupportSessionOrderByCreatedAt(session)
                .stream()
                .map(message -> toResponse(message, currentUser))
                .toList();
    }

    @Transactional
    public Map<String, Object> sendMessage(String username, String sessionCode, String text) {
        User currentUser = findUser(username);
        SupportSession session = findSession(sessionCode);
        checkActiveParticipant(session, currentUser);

        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Сообщение не может быть пустым");
        }

        String normalizedText = text.trim();
        if (normalizedText.length() > 2000) {
            throw new IllegalArgumentException("Сообщение слишком длинное. Максимум 2000 символов");
        }

        SupportChatMessage message = new SupportChatMessage();
        message.setSupportSession(session);
        message.setSender(currentUser);
        message.setMessage(normalizedText);

        return toResponse(supportChatMessageRepository.save(message), currentUser);
    }

    private SupportSession findSession(String sessionCode) {
        return supportSessionRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> new IllegalArgumentException("Сессия технической поддержки не найдена"));
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
    }

    private void checkActiveParticipant(SupportSession session, User currentUser) {
        if (session.getStatus() != SupportSessionStatus.ACTIVE) {
            throw new IllegalArgumentException("Чат доступен только в активной сессии техподдержки");
        }

        boolean isOperator = session.getOperator() != null
                && session.getOperator().getId().equals(currentUser.getId());
        boolean isClient = session.getClient() != null
                && session.getClient().getId().equals(currentUser.getId());

        if (!isOperator && !isClient) {
            throw new IllegalArgumentException("Нет доступа к чату этой сессии");
        }
    }

    private Map<String, Object> toResponse(SupportChatMessage message, User currentUser) {
        Map<String, Object> response = new LinkedHashMap<>();

        response.put("id", message.getId());
        response.put("senderId", message.getSender().getId());
        response.put("senderUsername", message.getSender().getUsername());
        response.put("mine", message.getSender().getId().equals(currentUser.getId()));
        response.put("message", message.getMessage());
        response.put("createdAt", message.getCreatedAt());
        response.put("createdAtText", message.getCreatedAt().format(DateTimeFormatter.ofPattern("HH:mm:ss")));

        return response;
    }
}
