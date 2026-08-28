package com.remote.websocket.client.service;

import com.remote.auth.service.AuthSessionSecurityService;
import com.remote.core.model.User;
import com.remote.core.repository.UserRepository;
import com.remote.pc.model.Pc;
import com.remote.pc.repository.PcRepository;
import com.remote.remoteaccess.model.RemoteSession;
import com.remote.remoteaccess.repository.RemoteSessionRepository;
import com.remote.websocket.agent.AgentWebSocketHandler;
import com.remote.websocket.common.WebSocketMessageSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.remote.common.ServerConstants.PROFILE_PERSONAL;

@Slf4j
@Service
public class ClientSessionService {

    private static final String UNKNOWN_USER =
            "unknown";

    private final PcRepository pcRepository;
    private final UserRepository userRepository;
    private final RemoteSessionRepository remoteSessionRepository;
    private final AgentWebSocketHandler agentWebSocketHandler;
    private final AuthSessionSecurityService authSessionSecurityService;
    private final ClientViewerRegistry clientViewerRegistry;
    private final LastFrameCache lastFrameCache;
    private final WebSocketMessageSender webSocketMessageSender;

    /*
     * WebSocketSession id является только runtime-
     * идентификатором соединения.
     *
     * Здесь он связывается с постоянной строкой
     * RemoteSession в БД.
     */
    private final Map<String, Long> remoteSessionIds =
            new ConcurrentHashMap<>();

    private final Map<String, String> sessionProfiles =
            new ConcurrentHashMap<>();

    private final Map<String, String> sessionUsernames =
            new ConcurrentHashMap<>();

    private final Map<String, Double> fpsSum =
            new ConcurrentHashMap<>();

    private final Map<String, Double> latencySum =
            new ConcurrentHashMap<>();

    private final Map<String, Integer> metricsCount =
            new ConcurrentHashMap<>();

    public ClientSessionService(
            PcRepository pcRepository,
            UserRepository userRepository,
            RemoteSessionRepository remoteSessionRepository,
            @Lazy AgentWebSocketHandler agentWebSocketHandler,
            AuthSessionSecurityService authSessionSecurityService,
            ClientViewerRegistry clientViewerRegistry,
            LastFrameCache lastFrameCache,
            WebSocketMessageSender webSocketMessageSender
    ) {
        this.pcRepository =
                pcRepository;

        this.userRepository =
                userRepository;

        this.remoteSessionRepository =
                remoteSessionRepository;

        this.agentWebSocketHandler =
                agentWebSocketHandler;

        this.authSessionSecurityService =
                authSessionSecurityService;

        this.clientViewerRegistry =
                clientViewerRegistry;

        this.lastFrameCache =
                lastFrameCache;

        this.webSocketMessageSender =
                webSocketMessageSender;
    }

    public void handleWatch(
            WebSocketSession session,
            JsonNode json,
            String profile
    ) throws IOException {

        Long pcId =
                json.get("pcId")
                        .asLong();

        clientViewerRegistry.addViewer(
                pcId,
                session
        );

        String username =
                extractUsernameFromJson(
                        json
                );

        sessionUsernames.put(
                session.getId(),
                username
        );

        sessionProfiles.put(
                session.getId(),
                profile
        );

        String clientIp =
                extractClientIp(
                        session
                );

        String clientInfo =
                extractClientInfo(
                        json
                );

        String mode =
                json.has("mode")
                        ? json.get("mode")
                        .asString()
                        : "Control";

        Pc pc =
                pcRepository
                        .findById(
                                pcId
                        )
                        .orElse(null);

        if (pc != null) {
            RemoteSession remoteSession =
                    createRemoteSession(
                            pc,
                            username,
                            profile,
                            mode,
                            clientIp,
                            clientInfo
                    );

            RemoteSession saved =
                    remoteSessionRepository
                            .save(
                                    remoteSession
                            );

            remoteSessionIds.put(
                    session.getId(),
                    saved.getId()
            );

            fpsSum.put(
                    session.getId(),
                    0.0
            );

            latencySum.put(
                    session.getId(),
                    0.0
            );

            metricsCount.put(
                    session.getId(),
                    0
            );

            String notificationMessage =
                    "К вашему ПК \""
                            + pc.getName()
                            + "\" подключился: "
                            + username
                            + " | Сценарий: "
                            + profile
                            + " | IP: "
                            + clientIp
                            + " | Устройство: "
                            + clientInfo;

            agentWebSocketHandler
                    .sendNotificationToAgent(
                            pcId,
                            notificationMessage
                    );

            log.info(
                    "Remote session started: remoteSessionId={}, sessionId={}, username={}, pcId={}, profile={}",
                    saved.getId(),
                    saved.getSessionId(),
                    username,
                    pcId,
                    profile
            );
        }

        String lastFrame =
                lastFrameCache.get(
                        pcId
                );

        if (lastFrame != null) {
            webSocketMessageSender.send(
                    session,
                    new TextMessage(
                            "{\"type\":\"frame\",\"image\":\""
                                    + lastFrame
                                    + "\"}"
                    )
            );
        }
    }

    public void handleMetrics(
            WebSocketSession session,
            JsonNode json
    ) {
        Long remoteSessionId =
                remoteSessionIds.get(
                        session.getId()
                );

        if (remoteSessionId == null) {
            return;
        }

        double fps =
                json.has("fps")
                        ? json.get("fps")
                        .asDouble(0.0)
                        : 0.0;

        double latency =
                json.has("latency")
                        ? json.get("latency")
                        .asDouble(0.0)
                        : 0.0;

        String mode =
                json.has("mode")
                        ? json.get("mode")
                        .asString("Control")
                        : "Control";

        fpsSum.merge(
                session.getId(),
                fps,
                Double::sum
        );

        latencySum.merge(
                session.getId(),
                latency,
                Double::sum
        );

        metricsCount.merge(
                session.getId(),
                1,
                Integer::sum
        );

        int count =
                metricsCount.getOrDefault(
                        session.getId(),
                        0
                );

        if (count <= 0) {
            return;
        }

        double avgFps =
                fpsSum.getOrDefault(
                        session.getId(),
                        0.0
                ) / count;

        double avgLatency =
                latencySum.getOrDefault(
                        session.getId(),
                        0.0
                ) / count;

        remoteSessionRepository
                .findById(
                        remoteSessionId
                )
                .ifPresent(
                        remoteSession -> {
                            remoteSession.setAvgFps(
                                    round(avgFps)
                            );

                            remoteSession.setAvgLatency(
                                    round(avgLatency)
                            );

                            remoteSession.setMode(
                                    mode
                            );

                            remoteSessionRepository.save(
                                    remoteSession
                            );
                        }
                );
    }

    public void closeSession(
            WebSocketSession session
    ) {
        Long remoteSessionId =
                remoteSessionIds.remove(
                        session.getId()
                );

        clientViewerRegistry
                .removeSessionEverywhere(
                        session
                );

        sessionProfiles.remove(
                session.getId()
        );

        sessionUsernames.remove(
                session.getId()
        );

        fpsSum.remove(
                session.getId()
        );

        latencySum.remove(
                session.getId()
        );

        metricsCount.remove(
                session.getId()
        );

        if (remoteSessionId == null) {
            return;
        }

        remoteSessionRepository
                .findById(
                        remoteSessionId
                )
                .ifPresent(
                        remoteSession -> {
                            Instant endedAt =
                                    Instant.now();

                            remoteSession.setEndedAt(
                                    endedAt
                            );

                            if (remoteSession.getStartedAt()
                                    != null) {

                                long seconds =
                                        Duration.between(
                                                remoteSession.getStartedAt(),
                                                endedAt
                                        ).getSeconds();

                                remoteSession.setDurationSeconds(
                                        safeDurationSeconds(
                                                seconds
                                        )
                                );
                            }

                            remoteSessionRepository.save(
                                    remoteSession
                            );

                            log.info(
                                    "Remote session closed: remoteSessionId={}, sessionId={}, durationSeconds={}",
                                    remoteSession.getId(),
                                    remoteSession.getSessionId(),
                                    remoteSession.getDurationSeconds()
                            );
                        }
                );
    }

    public String getProfile(
            String sessionId
    ) {
        return sessionProfiles
                .getOrDefault(
                        sessionId,
                        PROFILE_PERSONAL
                );
    }

    public String getUsername(
            String sessionId
    ) {
        return sessionUsernames
                .getOrDefault(
                        sessionId,
                        UNKNOWN_USER
                );
    }

    private RemoteSession createRemoteSession(
            Pc pc,
            String username,
            String profile,
            String mode,
            String clientIp,
            String clientInfo
    ) {
        RemoteSession remoteSession =
                new RemoteSession();

        remoteSession.setPc(
                pc
        );

        remoteSession.setPcName(
                pc.getName()
        );

        remoteSession.setProfile(
                profile
        );

        remoteSession.setMode(
                normalizeMode(
                        mode
                )
        );

        remoteSession.setClientIp(
                clientIp
        );

        remoteSession.setClientInfo(
                clientInfo
        );

        remoteSession.setAvgFps(
                0.0
        );

        remoteSession.setAvgLatency(
                0.0
        );

        remoteSession.setFilesSent(
                0
        );

        if (!UNKNOWN_USER.equals(
                username
        )) {
            remoteSession.setUserEmail(
                    username
            );

            User user =
                    userRepository
                            .findByEmail(
                                    username
                            )
                            .orElse(null);

            remoteSession.setUser(
                    user
            );
        }

        return remoteSession;
    }

    private String extractUsernameFromJson(
            JsonNode json
    ) {
        if (!json.has("token")) {
            return UNKNOWN_USER;
        }

        String token =
                json.get("token")
                        .asString();

        if (token == null
                || token.isBlank()) {

            return UNKNOWN_USER;
        }

        return authSessionSecurityService
                .validateAndExtractEmail(
                        token
                )
                .orElse(
                        UNKNOWN_USER
                );
    }

    private String extractClientIp(
            WebSocketSession session
    ) {
        InetSocketAddress address =
                session.getRemoteAddress();

        if (address == null
                || address.getAddress() == null) {

            return "unknown";
        }

        return address.getAddress()
                .getHostAddress();
    }

    private String extractClientInfo(
            JsonNode json
    ) {
        String platform =
                json.has("platform")
                        ? json.get("platform")
                        .asString()
                        : "unknown platform";

        String browser =
                json.has("browser")
                        ? json.get("browser")
                        .asString()
                        : "unknown browser";

        return platform
                + ", "
                + browser;
    }

    private String normalizeMode(
            String mode
    ) {
        if (mode == null
                || mode.isBlank()) {

            return "Control";
        }

        return mode;
    }

    private int safeDurationSeconds(
            long seconds
    ) {
        long normalizedSeconds =
                Math.max(
                        seconds,
                        0
                );

        return normalizedSeconds
                > Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : (int) normalizedSeconds;
    }

    private double round(
            double value
    ) {
        return Math.round(
                value * 10.0
        ) / 10.0;
    }
}