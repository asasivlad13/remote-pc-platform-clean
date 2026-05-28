package com.remote.support.service;

import com.remote.pc.model.Pc;
import com.remote.pc.model.PcStatus;
import com.remote.support.model.SupportSession;
import com.remote.support.model.SupportSessionStatus;
import com.remote.core.model.User;
import com.remote.support.repository.SupportSessionRepository;
import com.remote.core.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

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

    /*
     * Оператор создаёт только заявку/сессию и код.
     * ПК клиента здесь НЕ выбирается.
     */
    @Transactional
    public Map<String, Object> create(String operatorUsername, String title) {
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
    public Map<String, Object> getByCode(String sessionCode) {
        SupportSession session = findSession(sessionCode);
        return toResponse(session);
    }

    /*
     * Клиент входит по коду.
     * Здесь автоматически выбирается онлайн-ПК клиента.
     */
    @Transactional
    public Map<String, Object> join(String clientUsername, String sessionCode) {
        User client = findUser(clientUsername);
        SupportSession session = findSession(sessionCode);

        if (session.getStatus() != SupportSessionStatus.WAITING_CLIENT) {
            throw new IllegalArgumentException("Сессия недоступна для подключения");
        }

        if (session.getClient() != null && !session.getClient().getId().equals(client.getId())) {
            throw new IllegalArgumentException("К этой сессии уже подключён другой клиент");
        }

        if (session.getOperator().getId().equals(client.getId())) {
            throw new IllegalArgumentException("Оператор не может подключиться как клиент");
        }

        Pc clientPc = findOnlineClientPc(client);

        session.setClient(client);
        session.setClientPc(clientPc);
        session.setStatus(SupportSessionStatus.ACTIVE);
        session.setStartedAt(LocalDateTime.now());

        return toResponse(supportSessionRepository.save(session));
    }

    @Transactional
    public Map<String, Object> finish(String username, String sessionCode) {
        User currentUser = findUser(username);
        SupportSession session = findSession(sessionCode);

        boolean isOperator = session.getOperator().getId().equals(currentUser.getId());
        boolean isClient = session.getClient() != null
                && session.getClient().getId().equals(currentUser.getId());

        if (!isOperator && !isClient) {
            throw new IllegalArgumentException("Нет доступа к завершению этой сессии");
        }

        if (session.getStatus() != SupportSessionStatus.FINISHED
                && session.getStatus() != SupportSessionStatus.CANCELLED) {
            session.finish();
        }

        return toResponse(supportSessionRepository.save(session));
    }

    @Transactional
    public Map<String, Object> requestControl(String operatorUsername, String sessionCode) {
        User operator = findUser(operatorUsername);
        SupportSession session = findSession(sessionCode);

        checkActive(session);

        if (!session.getOperator().getId().equals(operator.getId())) {
            throw new IllegalArgumentException("Только оператор может запросить управление");
        }

        session.setControlRequested(true);
        session.setControlAllowed(false);
        session.setControlRequestedAt(LocalDateTime.now());
        session.setControlAllowedAt(null);

        return toResponse(supportSessionRepository.save(session));
    }

    @Transactional
    public Map<String, Object> allowControl(String clientUsername, String sessionCode) {
        User client = findUser(clientUsername);
        SupportSession session = findSession(sessionCode);

        checkActive(session);
        checkClient(session, client);

        if (!session.isControlRequested()) {
            throw new IllegalArgumentException("Оператор ещё не запрашивал управление");
        }

        session.setControlAllowed(true);
        session.setControlAllowedAt(LocalDateTime.now());

        return toResponse(supportSessionRepository.save(session));
    }

    @Transactional
    public Map<String, Object> denyControl(String clientUsername, String sessionCode) {
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
    public Map<String, Object> getMyActiveOperatorSession(String username) {
        User operator = findUser(username);

        Map<String, Object> active = supportSessionRepository
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
    public Map<String, Object> getMyActiveClientSession(String username) {
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

        if (session == null) {
            return false;
        }

        if (session.getStatus() != SupportSessionStatus.ACTIVE) {
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
            throw new IllegalArgumentException("У клиента нет зарегистрированных ПК");
        }

        return client.getPcs()
                .stream()
                .filter(pc -> pc.getStatus() == PcStatus.ONLINE)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
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
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
    }

    private SupportSession findSession(String sessionCode) {
        return supportSessionRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> new IllegalArgumentException("Сессия технической поддержки не найдена"));
    }

    private void checkActive(SupportSession session) {
        if (session.getStatus() != SupportSessionStatus.ACTIVE) {
            throw new IllegalArgumentException("Сессия техподдержки не активна");
        }

        if (session.getClient() == null || session.getClientPc() == null) {
            throw new IllegalArgumentException("К сессии ещё не подключён клиент");
        }
    }

    private void checkClient(SupportSession session, User client) {
        if (session.getClient() == null || !session.getClient().getId().equals(client.getId())) {
            throw new IllegalArgumentException("Только клиент этой сессии может выполнить действие");
        }
    }

    private String generateUniqueCode() {
        String code;

        do {
            code = String.valueOf(100000 + random.nextInt(900000));
        } while (supportSessionRepository.existsBySessionCode(code));

        return code;
    }

    private Map<String, Object> toResponse(SupportSession session) {
        Map<String, Object> response = new LinkedHashMap<>();

        response.put("id", session.getId());
        response.put("sessionCode", session.getSessionCode());
        response.put("title", session.getTitle());
        response.put("status", session.getStatus());
        response.put("createdAt", session.getCreatedAt());
        response.put("startedAt", session.getStartedAt());
        response.put("finishedAt", session.getFinishedAt());

        response.put("operatorId", session.getOperator().getId());
        response.put("operatorUsername", session.getOperator().getUsername());

        response.put("clientId", session.getClient() != null ? session.getClient().getId() : null);
        response.put("clientUsername", session.getClient() != null ? session.getClient().getUsername() : null);

        response.put("clientPcId", session.getClientPc() != null ? session.getClientPc().getId() : null);
        response.put("clientPcName", session.getClientPc() != null ? session.getClientPc().getName() : null);
        response.put("clientPcStatus", session.getClientPc() != null ? session.getClientPc().getStatus() : null);
        response.put("clientPcWebrtcUrl", session.getClientPc() != null ? session.getClientPc().getWebrtcUrl() : null);
        response.put("clientPcStreamName", session.getClientPc() != null ? session.getClientPc().getStreamName() : null);
        response.put("clientPcScreenWidth", session.getClientPc() != null ? session.getClientPc().getScreenWidth() : null);
        response.put("clientPcScreenHeight", session.getClientPc() != null ? session.getClientPc().getScreenHeight() : null);
        response.put("screenWidth", session.getClientPc() != null ? session.getClientPc().getScreenWidth() : null);
        response.put("screenHeight", session.getClientPc() != null ? session.getClientPc().getScreenHeight() : null);

        response.put("controlRequested", session.isControlRequested());
        response.put("controlAllowed", session.isControlAllowed());
        response.put("controlRequestedAt", session.getControlRequestedAt());
        response.put("controlAllowedAt", session.getControlAllowedAt());

        return response;
    }
}
