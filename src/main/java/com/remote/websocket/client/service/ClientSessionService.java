package com.remote.websocket.client.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.remote.auth.security.JwtUtil;
import com.remote.history.model.ConnectionLog;
import com.remote.history.repository.ConnectionLogRepository;
import com.remote.pc.model.Pc;
import com.remote.pc.repository.PcRepository;
import com.remote.websocket.agent.AgentWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ClientSessionService {

    private final PcRepository pcRepository;
    private final ConnectionLogRepository connectionLogRepository;
    private final AgentWebSocketHandler agentWebSocketHandler;
    private final JwtUtil jwtUtil;
    private final ClientViewerRegistry clientViewerRegistry;
    private final LastFrameCache lastFrameCache;

    private final Map<String, Long> sessionLogIds = new ConcurrentHashMap<>();
    private final Map<String, String> sessionProfiles = new ConcurrentHashMap<>();
    private final Map<String, String> sessionUsernames = new ConcurrentHashMap<>();

    private final Map<String, Double> fpsSum = new ConcurrentHashMap<>();
    private final Map<String, Double> latencySum = new ConcurrentHashMap<>();
    private final Map<String, Integer> metricsCount = new ConcurrentHashMap<>();

    public ClientSessionService(PcRepository pcRepository,
                                ConnectionLogRepository connectionLogRepository,
                                @Lazy AgentWebSocketHandler agentWebSocketHandler,
                                JwtUtil jwtUtil,
                                ClientViewerRegistry clientViewerRegistry,
                                LastFrameCache lastFrameCache) {
        this.pcRepository = pcRepository;
        this.connectionLogRepository = connectionLogRepository;
        this.agentWebSocketHandler = agentWebSocketHandler;
        this.jwtUtil = jwtUtil;
        this.clientViewerRegistry = clientViewerRegistry;
        this.lastFrameCache = lastFrameCache;
    }

    public void handleWatch(WebSocketSession session, JsonNode json, String profile) throws Exception {
        Long pcId = json.get("pcId").asLong();
        clientViewerRegistry.addViewer(pcId, session);

        String username = extractUsernameFromJson(json);
        sessionUsernames.put(session.getId(), username);
        sessionProfiles.put(session.getId(), profile);

        String clientIp = extractClientIp(session);
        String clientInfo = extractClientInfo(json);
        String mode = json.has("mode") ? json.get("mode").asText() : "Control";

        Pc pc = pcRepository.findById(pcId).orElse(null);

        if (pc != null) {
            ConnectionLog connectionLog = new ConnectionLog(username, pc.getName(), "CONNECT", clientIp);
            connectionLog.setPc(pc);
            connectionLog.setClientInfo(clientInfo);
            connectionLog.setMode(mode);
            connectionLog.setAvgFps(0.0);
            connectionLog.setAvgLatency(0.0);
            connectionLog.setFilesSent(0);
            connectionLog.setIssues("profile=" + profile);

            ConnectionLog saved = connectionLogRepository.save(connectionLog);
            sessionLogIds.put(session.getId(), saved.getId());

            fpsSum.put(session.getId(), 0.0);
            latencySum.put(session.getId(), 0.0);
            metricsCount.put(session.getId(), 0);

            String notificationMessage =
                    "К вашему ПК \"" + pc.getName() + "\" подключился: "
                            + username
                            + " | Сценарий: " + profile
                            + " | IP: " + clientIp
                            + " | Устройство: " + clientInfo;

            agentWebSocketHandler.sendNotificationToAgent(pcId, notificationMessage);

            log.info(
                    "Connection logged: username={}, pcId={}, pcName={}",
                    username,
                    pcId,
                    pc.getName()
            );

            log.info(
                    "Connection profile: sessionId={}, profile={}",
                    session.getId(),
                    profile
            );

            log.info(
                    "Agent notification sent: pcId={}, username={}, profile={}",
                    pcId,
                    username,
                    profile
            );
        }

        String lastFrame = lastFrameCache.get(pcId);
        if (lastFrame != null) {
            session.sendMessage(new TextMessage("{\"type\":\"frame\",\"image\":\"" + lastFrame + "\"}"));
        }
    }

    public void handleMetrics(WebSocketSession session, JsonNode json) {
        Long logId = sessionLogIds.get(session.getId());

        if (logId == null) {
            return;
        }

        double fps = json.has("fps") ? json.get("fps").asDouble(0.0) : 0.0;
        double latency = json.has("latency") ? json.get("latency").asDouble(0.0) : 0.0;
        String mode = json.has("mode") ? json.get("mode").asText("Control") : "Control";

        fpsSum.merge(session.getId(), fps, Double::sum);
        latencySum.merge(session.getId(), latency, Double::sum);
        metricsCount.merge(session.getId(), 1, Integer::sum);

        int count = metricsCount.getOrDefault(session.getId(), 0);

        if (count <= 0) {
            return;
        }

        double avgFps = fpsSum.getOrDefault(session.getId(), 0.0) / count;
        double avgLatency = latencySum.getOrDefault(session.getId(), 0.0) / count;

        connectionLogRepository.findById(logId).ifPresent(connectionLog -> {
            connectionLog.setAvgFps(round(avgFps));
            connectionLog.setAvgLatency(round(avgLatency));
            connectionLog.setMode(mode);
            connectionLogRepository.save(connectionLog);
        });
    }

    public void closeSession(WebSocketSession session) {
        Long logId = sessionLogIds.remove(session.getId());

        clientViewerRegistry.removeSessionEverywhere(session);
        sessionProfiles.remove(session.getId());
        sessionUsernames.remove(session.getId());
        fpsSum.remove(session.getId());
        latencySum.remove(session.getId());
        metricsCount.remove(session.getId());

        if (logId == null) {
            return;
        }

        connectionLogRepository.findById(logId).ifPresent(connectionLog -> {
            LocalDateTime disconnectedAt = LocalDateTime.now();
            connectionLog.setDisconnectedAt(disconnectedAt);

            if (connectionLog.getTimestamp() != null) {
                long seconds = Duration.between(
                        connectionLog.getTimestamp(),
                        disconnectedAt
                ).getSeconds();

                connectionLog.setDurationSeconds((int) Math.max(seconds, 0));
            }

            connectionLogRepository.save(connectionLog);

            log.info(
                    "Session closed: logId={}, durationSeconds={}",
                    logId,
                    connectionLog.getDurationSeconds()
            );
        });
    }

    public String getProfile(String sessionId) {
        return sessionProfiles.getOrDefault(sessionId, "personal");
    }

    public String getUsername(String sessionId) {
        return sessionUsernames.getOrDefault(sessionId, "unknown");
    }

    private String extractUsernameFromJson(JsonNode json) {
        try {
            if (json.has("token")) {
                String token = json.get("token").asText();

                if (token != null && jwtUtil.validateToken(token)) {
                    return jwtUtil.extractUsername(token);
                }
            }
        } catch (Exception e) {
            log.warn("Cannot extract username from WebSocket token", e);
        }

        return "unknown";
    }

    private String extractClientIp(WebSocketSession session) {
        InetSocketAddress address = session.getRemoteAddress();

        if (address == null || address.getAddress() == null) {
            return "unknown";
        }

        return address.getAddress().getHostAddress();
    }

    private String extractClientInfo(JsonNode json) {
        String platform = json.has("platform")
                ? json.get("platform").asText()
                : "unknown platform";

        String browser = json.has("browser")
                ? json.get("browser").asText()
                : "unknown browser";

        return platform + ", " + browser;
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}