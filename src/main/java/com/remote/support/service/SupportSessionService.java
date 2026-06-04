package com.remote.support.service;

import com.remote.core.exception.BusinessException;
import com.remote.core.model.User;
import com.remote.core.repository.UserRepository;
import com.remote.pc.model.Pc;
import com.remote.pc.model.PcStatus;
import com.remote.support.dto.SupportSessionResponse;
import com.remote.support.model.SupportSession;
import com.remote.support.model.SupportSessionStatus;
import com.remote.support.repository.SupportSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
public class SupportSessionService {

    private final SupportSessionRepository supportSessionRepository;
    private final UserRepository userRepository;

    private final SecureRandom random = new SecureRandom();

    public SupportSessionService(SupportSessionRepository supportSessionRepository,
                                 UserRepository userRepository) {
        this.supportSessionRepository = supportSessionRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public SupportSessionResponse create(String operatorUsername, String title) {
        User operator = findUser(operatorUsername);

        finishOldActiveOperatorSessions(operator);

        SupportSession session = new SupportSession();
        session.setSessionCode(generateUniqueCode());
        session.setTitle(title == null || title.isBlank()
                ? "Сессия технической поддержки"
                : title.trim());
        session.setOperator(operator);
        session.setStatus(SupportSessionStatus.WAITING_CLIENT);

        return toResponse(supportSessionRepository.save(session));
    }

    @Transactional(readOnly = true)
    public SupportSessionResponse getByCode(String sessionCode) {
        SupportSession session = findSession(sessionCode);

        if (isFinishedOrCancelled(session)) {
            throw conflict("SUPPORT_SESSION_FINISHED", "Сессия технической поддержки уже завершена");
        }

        return toResponse(session);
    }

    @Transactional
    public SupportSessionResponse join(String clientUsername, String sessionCode) {
        User client = findUser(clientUsername);
        SupportSession session = findSession(sessionCode);

        if (isFinishedOrCancelled(session)) {
            throw conflict("SUPPORT_SESSION_FINISHED", "Сессия технической поддержки уже завершена");
        }

        if (session.getStatus() != SupportSessionStatus.WAITING_CLIENT) {
            throw conflict("SUPPORT_SESSION_NOT_JOINABLE", "Сессия недоступна для подключения");
        }

        if (session.getClient() != null && !session.getClient().getId().equals(client.getId())) {
            throw conflict("SUPPORT_SESSION_HAS_ANOTHER_CLIENT", "К этой сессии уже подключён другой клиент");
        }

        if (session.getOperator().getId().equals(client.getId())) {
            throw forbidden("OPERATOR_CANNOT_JOIN_AS_CLIENT", "Оператор не может подключиться как клиент");
        }

        Pc clientPc = findOnlineClientPc(client);

        session.setClient(client);
        session.setClientPc(clientPc);
        session.setStatus(SupportSessionStatus.ACTIVE);
        session.setStartedAt(LocalDateTime.now());

        return toResponse(supportSessionRepository.save(session));
    }

    @Transactional
    public SupportSessionResponse finish(String username, String sessionCode) {
        User currentUser = findUser(username);
        SupportSession session = findSession(sessionCode);

        boolean isOperator = session.getOperator().getId().equals(currentUser.getId());
        boolean isClient = session.getClient() != null
                && session.getClient().getId().equals(currentUser.getId());

        if (!isOperator && !isClient) {
            throw forbidden("SUPPORT_SESSION_FINISH_FORBIDDEN", "Нет доступа к завершению этой сессии");
        }

        if (!isFinishedOrCancelled(session)) {
            session.finish();
        }

        return toResponse(supportSessionRepository.save(session));
    }

    @Transactional
    public SupportSessionResponse requestControl(String operatorUsername, String sessionCode) {
        User operator = findUser(operatorUsername);
        SupportSession session = findSession(sessionCode);

        checkActive(session);

        if (!session.getOperator().getId().equals(operator.getId())) {
            throw forbidden("ONLY_OPERATOR_CAN_REQUEST_CONTROL", "Только оператор может запросить управление");
        }

        session.setControlRequested(true);
        session.setControlAllowed(false);
        session.setControlRequestedAt(LocalDateTime.now());
        session.setControlAllowedAt(null);

        return toResponse(supportSessionRepository.save(session));
    }

    @Transactional
    public SupportSessionResponse allowControl(String clientUsername, String sessionCode) {
        User client = findUser(clientUsername);
        SupportSession session = findSession(sessionCode);

        checkActive(session);
        checkClient(session, client);

        if (!session.isControlRequested()) {
            throw conflict("CONTROL_NOT_REQUESTED", "Оператор ещё не запрашивал управление");
        }

        session.setControlAllowed(true);
        session.setControlAllowedAt(LocalDateTime.now());

        return toResponse(supportSessionRepository.save(session));
    }

    @Transactional
    public SupportSessionResponse denyControl(String clientUsername, String sessionCode) {
        User client = findUser(clientUsername);
        SupportSession session = findSession(sessionCode);

        checkActive(session);
        checkClient(session, client);

        session.setControlRequested(false);
        session.setControlAllowed(false);
        session.setControlRequestedAt(null);
        session.setControlAllowedAt(null);

        return toResponse(supportSessionRepository.save(session));
    }

    @Transactional(readOnly = true)
    public SupportSessionResponse getMyActiveOperatorSession(String username) {
        User operator = findUser(username);

        SupportSessionResponse active = supportSessionRepository
                .findFirstByOperatorAndStatusOrderByCreatedAtDesc(operator, SupportSessionStatus.ACTIVE)
                .map(this::toResponse)
                .orElse(null);

        if (active != null) {
            return active;
        }

        return supportSessionRepository
                .findFirstByOperatorAndStatusOrderByCreatedAtDesc(operator, SupportSessionStatus.WAITING_CLIENT)
                .map(this::toResponse)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public SupportSessionResponse getMyActiveClientSession(String username) {
        User client = findUser(username);

        return supportSessionRepository
                .findFirstByClientAndStatusOrderByCreatedAtDesc(client, SupportSessionStatus.ACTIVE)
                .map(this::toResponse)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean hasOperatorControl(String operatorUsername, String sessionCode, Long targetPcId) {
        User operator = findUser(operatorUsername);

        SupportSession session = supportSessionRepository.findBySessionCode(sessionCode)
                .orElse(null);

        if (session == null || session.getStatus() != SupportSessionStatus.ACTIVE) {
            return false;
        }

        if (session.getOperator() == null || !session.getOperator().getId().equals(operator.getId())) {
            return false;
        }

        if (session.getClientPc() == null || targetPcId == null
                || !session.getClientPc().getId().equals(targetPcId)) {
            return false;
        }

        return session.isControlAllowed();
    }

    private Pc findOnlineClientPc(User client) {
        if (client.getPcs() == null || client.getPcs().isEmpty()) {
            throw conflict("CLIENT_HAS_NO_REGISTERED_PC", "У клиента нет зарегистрированных ПК");
        }

        return client.getPcs()
                .stream()
                .filter(pc -> pc.getStatus() == PcStatus.ONLINE)
                .findFirst()
                .orElseThrow(() -> conflict(
                        "CLIENT_HAS_NO_ONLINE_AGENT",
                        "У клиента нет подключённого онлайн-агента. Запустите агент на ПК клиента и повторите вход."
                ));
    }

    private void finishOldActiveOperatorSessions(User operator) {
        supportSessionRepository
                .findByOperatorAndStatusOrderByCreatedAtDesc(operator, SupportSessionStatus.ACTIVE)
                .forEach(session -> {
                    session.finish();
                    supportSessionRepository.save(session);
                });

        supportSessionRepository
                .findByOperatorAndStatusOrderByCreatedAtDesc(operator, SupportSessionStatus.WAITING_CLIENT)
                .forEach(session -> {
                    session.cancel();
                    supportSessionRepository.save(session);
                });
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> notFound("USER_NOT_FOUND", "Пользователь не найден"));
    }

    private SupportSession findSession(String sessionCode) {
        return supportSessionRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> notFound("SUPPORT_SESSION_NOT_FOUND", "Сессия технической поддержки не найдена"));
    }

    private void checkActive(SupportSession session) {
        if (session.getStatus() != SupportSessionStatus.ACTIVE) {
            throw conflict("SUPPORT_SESSION_NOT_ACTIVE", "Сессия техподдержки не активна");
        }

        if (session.getClient() == null || session.getClientPc() == null) {
            throw conflict("SUPPORT_CLIENT_NOT_CONNECTED", "К сессии ещё не подключён клиент");
        }
    }

    private void checkClient(SupportSession session, User client) {
        if (session.getClient() == null || !session.getClient().getId().equals(client.getId())) {
            throw forbidden("ONLY_SESSION_CLIENT_ALLOWED", "Только клиент этой сессии может выполнить действие");
        }
    }

    private boolean isFinishedOrCancelled(SupportSession session) {
        return session.getStatus() == SupportSessionStatus.FINISHED
                || session.getStatus() == SupportSessionStatus.CANCELLED;
    }

    private String generateUniqueCode() {
        String code;

        do {
            code = String.valueOf(100000 + random.nextInt(900000));
        } while (supportSessionRepository.existsBySessionCode(code));

        return code;
    }

    private SupportSessionResponse toResponse(SupportSession session) {
        return new SupportSessionResponse(
                session.getId(),
                session.getSessionCode(),
                session.getTitle(),
                session.getStatus(),
                session.getCreatedAt(),
                session.getStartedAt(),
                session.getFinishedAt(),

                session.getOperator().getId(),
                session.getOperator().getUsername(),

                session.getClient() != null ? session.getClient().getId() : null,
                session.getClient() != null ? session.getClient().getUsername() : null,

                session.getClientPc() != null ? session.getClientPc().getId() : null,
                session.getClientPc() != null ? session.getClientPc().getName() : null,
                session.getClientPc() != null && session.getClientPc().getStatus() != null
                        ? session.getClientPc().getStatus().name()
                        : null,
                session.getClientPc() != null ? session.getClientPc().getWebrtcUrl() : null,
                session.getClientPc() != null ? session.getClientPc().getStreamName() : null,
                session.getClientPc() != null ? session.getClientPc().getScreenWidth() : null,
                session.getClientPc() != null ? session.getClientPc().getScreenHeight() : null,
                session.getClientPc() != null ? session.getClientPc().getScreenWidth() : null,
                session.getClientPc() != null ? session.getClientPc().getScreenHeight() : null,

                session.isControlRequested(),
                session.isControlAllowed(),
                session.getControlRequestedAt(),
                session.getControlAllowedAt()
        );
    }

    private BusinessException forbidden(String code, String message) {
        return new BusinessException(HttpStatus.FORBIDDEN, code, message);
    }

    private BusinessException notFound(String code, String message) {
        return new BusinessException(HttpStatus.NOT_FOUND, code, message);
    }

    private BusinessException conflict(String code, String message) {
        return new BusinessException(HttpStatus.CONFLICT, code, message);
    }
}