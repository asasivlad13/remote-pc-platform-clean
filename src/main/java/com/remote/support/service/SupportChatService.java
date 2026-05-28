package com.remote.support.service;

import com.remote.education.service.EducationCryptoService;
import com.remote.support.model.SupportChatMessage;
import com.remote.support.model.SupportSession;
import com.remote.support.model.SupportSessionStatus;
import com.remote.core.model.User;
import com.remote.support.repository.SupportChatMessageRepository;
import com.remote.support.repository.SupportSessionRepository;
import com.remote.core.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SupportChatService {

    private final SupportChatMessageRepository supportChatMessageRepository;
    private final SupportSessionRepository supportSessionRepository;
    private final UserRepository userRepository;
    private final EducationCryptoService educationCryptoService;

    public SupportChatService(SupportChatMessageRepository supportChatMessageRepository,
                              SupportSessionRepository supportSessionRepository,
                              UserRepository userRepository,
                              EducationCryptoService educationCryptoService) {
        this.supportChatMessageRepository = supportChatMessageRepository;
        this.supportSessionRepository = supportSessionRepository;
        this.userRepository = userRepository;
        this.educationCryptoService = educationCryptoService;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getMessages(String username, String sessionCode) {
        SupportSession session = getSession(sessionCode);
        User currentUser = getUser(username);

        checkSessionParticipant(session, currentUser);

        return supportChatMessageRepository
                .findBySupportSessionOrderByCreatedAt(session)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public Map<String, Object> sendMessage(String username, String sessionCode, String messageText) {
        SupportSession session = getSession(sessionCode);
        User sender = getUser(username);

        checkSessionParticipant(session, sender);
        checkSessionActive(session);

        if (messageText == null || messageText.trim().isEmpty()) {
            throw new IllegalArgumentException("Сообщение не может быть пустым");
        }

        if (messageText.length() > 2000) {
            throw new IllegalArgumentException("Сообщение слишком длинное");
        }

        SupportChatMessage message = new SupportChatMessage();
        message.setSupportSession(session);
        message.setSender(sender);

        String encryptedMessage = educationCryptoService.encryptText(messageText.trim());
        message.setMessage(encryptedMessage);

        SupportChatMessage savedMessage = supportChatMessageRepository.save(message);

        return toResponse(savedMessage);
    }

    private Map<String, Object> toResponse(SupportChatMessage message) {
        Map<String, Object> response = new LinkedHashMap<>();

        response.put("id", message.getId());
        response.put("supportSessionId", message.getSupportSession().getId());
        response.put("senderId", message.getSender().getId());
        response.put("senderUsername", message.getSender().getUsername());

        String decryptedMessage = educationCryptoService.decryptText(message.getMessage());
        response.put("message", decryptedMessage);

        response.put("createdAt", message.getCreatedAt());

        return response;
    }

    private SupportSession getSession(String sessionCode) {
        return supportSessionRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> new IllegalArgumentException("Сессия техподдержки не найдена"));
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
    }

    private void checkSessionActive(SupportSession session) {
        if (session.getStatus() != SupportSessionStatus.ACTIVE) {
            throw new IllegalArgumentException("Чат доступен только в активной сессии");
        }
    }

    private void checkSessionParticipant(SupportSession session, User user) {
        boolean isOperator = session.getOperator() != null
                && session.getOperator().getId().equals(user.getId());

        boolean isClient = session.getClient() != null
                && session.getClient().getId().equals(user.getId());

        if (!isOperator && !isClient) {
            throw new IllegalArgumentException("Нет доступа к чату этой сессии");
        }
    }
}