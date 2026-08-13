package com.remote.support.service;

import com.remote.common.crypto.SharedCryptoService;
import com.remote.core.model.User;
import com.remote.core.repository.UserRepository;
import com.remote.support.dto.SupportChatMessageResponse;
import com.remote.support.model.SupportChatMessage;
import com.remote.support.model.SupportSession;
import com.remote.support.model.SupportSessionStatus;
import com.remote.support.repository.SupportChatMessageRepository;
import com.remote.support.repository.SupportSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SupportChatService {

    private final SupportChatMessageRepository supportChatMessageRepository;
    private final SupportSessionRepository supportSessionRepository;
    private final UserRepository userRepository;
    private final SharedCryptoService cryptoService;

    public SupportChatService(SupportChatMessageRepository supportChatMessageRepository,
                              SupportSessionRepository supportSessionRepository,
                              UserRepository userRepository,
                              SharedCryptoService cryptoService) {
        this.supportChatMessageRepository = supportChatMessageRepository;
        this.supportSessionRepository = supportSessionRepository;
        this.userRepository = userRepository;
        this.cryptoService = cryptoService;
    }

    @Transactional(readOnly = true)
    public List<SupportChatMessageResponse> getMessages(String username,
                                                        String sessionCode) {
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
    public SupportChatMessageResponse sendMessage(String username,
                                                  String sessionCode,
                                                  String messageText) {
        SupportSession session = getSession(sessionCode);
        User sender = getUser(username);

        checkSessionParticipant(session, sender);
        checkSessionActive(session);
        validateMessage(messageText);

        SupportChatMessage message = new SupportChatMessage();
        message.setSupportSession(session);
        message.setSender(sender);

        String encryptedMessage =
                cryptoService.encryptText(messageText.trim());

        message.setMessage(encryptedMessage);

        SupportChatMessage savedMessage =
                supportChatMessageRepository.save(message);

        return toResponse(savedMessage);
    }

    private SupportChatMessageResponse toResponse(
            SupportChatMessage message
    ) {
        String decryptedMessage =
                cryptoService.decryptText(
                        message.getMessage()
                );

        return new SupportChatMessageResponse(
                message.getId(),
                message.getSender().getId(),
                message.getSender().getUsername(),
                decryptedMessage,
                message.getCreatedAt()
        );
    }

    private void validateMessage(String messageText) {
        if (messageText == null || messageText.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Сообщение не может быть пустым"
            );
        }

        if (messageText.length() > 2000) {
            throw new IllegalArgumentException(
                    "Сообщение слишком длинное"
            );
        }
    }

    private SupportSession getSession(String sessionCode) {
        return supportSessionRepository
                .findBySessionCode(sessionCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Сессия техподдержки не найдена"
                ));
    }

    private User getUser(String username) {
        return userRepository
                .findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Пользователь не найден"
                ));
    }

    private void checkSessionActive(SupportSession session) {
        if (session.getStatus() != SupportSessionStatus.ACTIVE) {
            throw new IllegalArgumentException(
                    "Чат доступен только в активной сессии"
            );
        }
    }

    private void checkSessionParticipant(
            SupportSession session,
            User user
    ) {
        boolean isOperator =
                session.getOperator() != null
                        && session.getOperator()
                        .getId()
                        .equals(user.getId());

        boolean isClient =
                session.getClient() != null
                        && session.getClient()
                        .getId()
                        .equals(user.getId());

        if (!isOperator && !isClient) {
            throw new IllegalArgumentException(
                    "Нет доступа к чату этой сессии"
            );
        }
    }
}