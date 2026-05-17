package com.remote.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.remote.config.JwtUtil;
import com.remote.model.ConnectionLog;
import com.remote.model.Pc;
import com.remote.repository.ConnectionLogRepository;
import com.remote.repository.PcRepository;
import com.remote.service.EducationParticipantService;
import com.remote.service.SessionPermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketClientHandler extends TextWebSocketHandler {

    @Autowired
    private EducationParticipantService educationParticipantService;

    @Autowired
    private AgentWebSocketHandler agentWebSocketHandler;

    @Autowired
    private PcRepository pcRepository;

    @Autowired
    private ConnectionLogRepository connectionLogRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private SessionPermissionService sessionPermissionService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Long> clientWatching = new ConcurrentHashMap<>();
    private final Map<Long, String> lastFrames = new ConcurrentHashMap<>();

    private final Map<String, Long> sessionLogIds = new ConcurrentHashMap<>();
    private final Map<String, String> sessionProfiles = new ConcurrentHashMap<>();
    private final Map<String, String> sessionUsernames = new ConcurrentHashMap<>();

    private final Map<String, Double> fpsSum = new ConcurrentHashMap<>();
    private final Map<String, Double> latencySum = new ConcurrentHashMap<>();
    private final Map<String, Integer> metricsCount = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
        System.out.println("Web client connected: " + session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        System.out.println("Received from client: " + payload);

        JsonNode json = objectMapper.readTree(payload);

        if (!json.has("type")) {
            return;
        }

        String type = json.get("type").asText();

        if ("ping".equals(type)) {
            session.sendMessage(new TextMessage(payload.replace("\"ping\"", "\"pong\"")));
            return;
        }

        if ("watch".equals(type)) {
            handleWatch(session, json);
            return;
        }

        if ("metrics".equals(type)) {
            handleMetrics(session, json);
            return;
        }

        if ("stop".equals(type)) {
            closeSessionLog(session);
            return;
        }

        if ("command".equals(type)) {
            handleCommand(session, json);
            return;
        }

        if ("settings".equals(type)) {
            Long pcId = json.get("pcId").asLong();
            agentWebSocketHandler.sendCommandToAgent(pcId, json);
            session.sendMessage(new TextMessage("{\"type\":\"settings_applied\"}"));
        }
    }

    private void handleCommand(WebSocketSession session, JsonNode json) throws Exception {
        if (!json.has("pcId") || !json.has("action")) {
            session.sendMessage(new TextMessage("{\"type\":\"error\",\"message\":\"Invalid command payload\"}"));
            return;
        }

        Long pcId = json.get("pcId").asLong();
        String action = json.get("action").asText();

        String profile = sessionProfiles.getOrDefault(session.getId(), "personal");
        profile = sessionPermissionService.normalizeProfile(profile);

        boolean allowed;

        if ("personal".equals(profile)) {
            allowed = sessionPermissionService.isCommandAllowed(profile, action);

        } else if ("education_student".equals(profile)) {
            String educationCode = json.has("educationCode")
                    ? json.get("educationCode").asText()
                    : null;

            String username = sessionUsernames.getOrDefault(session.getId(), "unknown");

            allowed = educationCode != null
                    && !educationCode.isBlank()
                    && !"unknown".equals(username)
                    && isRemoteControlAction(action)
                    && educationParticipantService.hasControlInSession(username, educationCode);
        } else {
            allowed = false;
        }

        if (!allowed) {
            String errorJson = objectMapper.createObjectNode()
                    .put("type", "command_denied")
                    .put("profile", profile)
                    .put("action", action)
                    .put("message", "Command is not allowed for current scenario")
                    .toString();

            session.sendMessage(new TextMessage(errorJson));

            System.out.println("⛔ Command denied: profile=" + profile
                    + ", action=" + action
                    + ", username=" + sessionUsernames.getOrDefault(session.getId(), "unknown")
                    + ", educationCode=" + (json.has("educationCode") ? json.get("educationCode").asText() : "none")
                    + ", session=" + session.getId());

            return;
        }

        ObjectNode command = json.deepCopy();
        command.put("profile", profile);

        agentWebSocketHandler.sendCommandToAgent(pcId, command);

        System.out.println("✅ Command allowed: profile=" + profile
                + ", action=" + action
                + ", username=" + sessionUsernames.getOrDefault(session.getId(), "unknown")
                + ", pcId=" + pcId);
    }

    private void handleWatch(WebSocketSession session, JsonNode json) throws Exception {
        Long pcId = json.get("pcId").asLong();
        clientWatching.put(session.getId(), pcId);

        String username = extractUsernameFromJson(json);
        sessionUsernames.put(session.getId(), username);

        String clientIp = extractClientIp(session);
        String clientInfo = extractClientInfo(json);
        String mode = json.has("mode") ? json.get("mode").asText() : "Control";
        String profile = json.has("profile") ? json.get("profile").asText("personal") : "personal";
        profile = sessionPermissionService.normalizeProfile(profile);

        sessionProfiles.put(session.getId(), profile);

        Pc pc = pcRepository.findById(pcId).orElse(null);

        if (pc != null) {
            ConnectionLog log = new ConnectionLog(username, pc.getName(), "CONNECT", clientIp);
            log.setPc(pc);
            log.setClientInfo(clientInfo);
            log.setMode(mode);
            log.setAvgFps(0.0);
            log.setAvgLatency(0.0);
            log.setFilesSent(0);
            log.setIssues("profile=" + profile);

            ConnectionLog saved = connectionLogRepository.save(log);
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

            System.out.println("📝 Logged connection: " + username + " -> " + pc.getName());
            System.out.println("🎯 Connection profile: " + profile);
            System.out.println("🔔 Notification sent: " + notificationMessage);
        }

        String lastFrame = lastFrames.get(pcId);
        if (lastFrame != null) {
            session.sendMessage(new TextMessage("{\"type\":\"frame\",\"image\":\"" + lastFrame + "\"}"));
        }
    }

    private void handleMetrics(WebSocketSession session, JsonNode json) {
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

        connectionLogRepository.findById(logId).ifPresent(log -> {
            log.setAvgFps(round(avgFps));
            log.setAvgLatency(round(avgLatency));
            log.setMode(mode);
            connectionLogRepository.save(log);
        });
    }

    private void closeSessionLog(WebSocketSession session) {
        Long logId = sessionLogIds.remove(session.getId());

        clientWatching.remove(session.getId());
        sessionProfiles.remove(session.getId());
        sessionUsernames.remove(session.getId());
        fpsSum.remove(session.getId());
        latencySum.remove(session.getId());
        metricsCount.remove(session.getId());

        if (logId == null) {
            return;
        }

        connectionLogRepository.findById(logId).ifPresent(log -> {
            LocalDateTime disconnectedAt = LocalDateTime.now();
            log.setDisconnectedAt(disconnectedAt);

            if (log.getTimestamp() != null) {
                long seconds = Duration.between(log.getTimestamp(), disconnectedAt).getSeconds();
                log.setDurationSeconds((int) Math.max(seconds, 0));
            }

            connectionLogRepository.save(log);

            System.out.println("📝 Session closed: logId=" + logId
                    + ", duration=" + log.getDurationSeconds() + " sec");
        });
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
            System.err.println("Cannot extract username from token: " + e.getMessage());
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
        String platform = json.has("platform") ? json.get("platform").asText() : "unknown platform";
        String browser = json.has("browser") ? json.get("browser").asText() : "unknown browser";

        return platform + ", " + browser;
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    public void broadcastFrame(Long pcId, String base64Image) {
        lastFrames.put(pcId, base64Image);

        for (Map.Entry<String, Long> entry : clientWatching.entrySet()) {
            if (entry.getValue().equals(pcId)) {
                WebSocketSession session = sessions.get(entry.getKey());

                if (session != null && session.isOpen()) {
                    try {
                        session.sendMessage(new TextMessage("{\"type\":\"frame\",\"image\":\"" + base64Image + "\"}"));
                    } catch (Exception e) {
                        System.err.println("Error sending frame: " + e.getMessage());
                    }
                }
            }
        }
    }

    public void broadcastBinaryFrame(Long pcId, byte[] imageData) {
        for (Map.Entry<String, Long> entry : clientWatching.entrySet()) {
            if (entry.getValue().equals(pcId)) {
                WebSocketSession session = sessions.get(entry.getKey());

                if (session != null && session.isOpen()) {
                    try {
                        session.sendMessage(new BinaryMessage(imageData));
                    } catch (Exception e) {
                        System.err.println("Error sending binary frame: " + e.getMessage());
                    }
                }
            }
        }
    }

    public void broadcastFileProgress(Long pcId, JsonNode progressJson) {
        for (Map.Entry<String, Long> entry : clientWatching.entrySet()) {
            if (entry.getValue().equals(pcId)) {
                WebSocketSession session = sessions.get(entry.getKey());

                if (session != null && session.isOpen()) {
                    try {
                        session.sendMessage(new TextMessage(progressJson.toString()));
                    } catch (Exception e) {
                        System.err.println("Error sending file progress: " + e.getMessage());
                    }
                }
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        closeSessionLog(session);
        sessions.remove(session.getId());
        System.out.println("Web client disconnected: " + session.getId());
    }

    private boolean isRemoteControlAction(String action) {
        return "MOUSE_MOVE".equals(action)
                || "MOUSE_CLICK".equals(action)
                || "MOUSE_WHEEL".equals(action)
                || "KEY_PRESS".equals(action)
                || "KEY_RELEASE".equals(action)
                || "KEY_COMBO".equals(action);
    }
}