package com.remote.websocket.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.remote.service.SessionPermissionService;
import com.remote.websocket.client.service.ClientBroadcastService;
import com.remote.websocket.client.service.ClientSessionService;
import com.remote.websocket.client.service.CommandAuthorizationService;
import com.remote.websocket.client.service.CommandDispatchService;
import com.remote.websocket.client.service.RemoteFileWebSocketService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
@Component
public class WebSocketClientHandler extends TextWebSocketHandler {

    private final SessionPermissionService sessionPermissionService;
    private final ClientSessionService clientSessionService;
    private final RemoteFileWebSocketService remoteFileWebSocketService;
    private final CommandAuthorizationService commandAuthorizationService;
    private final ClientBroadcastService clientBroadcastService;
    private final CommandDispatchService commandDispatchService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public WebSocketClientHandler(SessionPermissionService sessionPermissionService,
                                  ClientSessionService clientSessionService,
                                  RemoteFileWebSocketService remoteFileWebSocketService,
                                  CommandAuthorizationService commandAuthorizationService,
                                  ClientBroadcastService clientBroadcastService,
                                  CommandDispatchService commandDispatchService) {
        this.sessionPermissionService = sessionPermissionService;
        this.clientSessionService = clientSessionService;
        this.remoteFileWebSocketService = remoteFileWebSocketService;
        this.commandAuthorizationService = commandAuthorizationService;
        this.clientBroadcastService = clientBroadcastService;
        this.commandDispatchService = commandDispatchService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
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
            String profile = json.has("profile")
                    ? json.get("profile").asText("personal")
                    : "personal";

            profile = normalizeConnectionProfile(profile);

            clientSessionService.handleWatch(session, json, profile);
            return;
        }

        if ("metrics".equals(type)) {
            clientSessionService.handleMetrics(session, json);
            return;
        }

        if ("stop".equals(type)) {
            clientSessionService.closeSession(session);
            remoteFileWebSocketService.removeOwnerSession(session);
            return;
        }

        if ("command".equals(type)) {
            handleCommand(session, json);
            return;
        }

        if ("remote_file_list".equals(type)) {
            String profile = normalizeConnectionProfile(clientSessionService.getProfile(session.getId()));
            String username = clientSessionService.getUsername(session.getId());

            remoteFileWebSocketService.handleRemoteFileList(session, json, profile, username);
            return;
        }

        if ("remote_file_download".equals(type)) {
            String profile = normalizeConnectionProfile(clientSessionService.getProfile(session.getId()));
            String username = clientSessionService.getUsername(session.getId());

            remoteFileWebSocketService.handleRemoteFileDownload(session, json, profile, username);
            return;
        }

        if ("settings".equals(type)) {
            Long pcId = json.get("pcId").asLong();
            commandDispatchService.sendSettings(pcId, json);
            session.sendMessage(new TextMessage("{\"type\":\"settings_applied\"}"));
        }
    }

    public void forwardRemoteFileMessage(Long pcId, JsonNode json) throws Exception {
        remoteFileWebSocketService.forwardRemoteFileMessage(json);
    }

    private void handleCommand(WebSocketSession session, JsonNode json) throws Exception {
        if (!json.has("pcId") || !json.has("action")) {
            session.sendMessage(new TextMessage("{\"type\":\"error\",\"message\":\"Invalid command payload\"}"));
            return;
        }

        Long pcId = json.get("pcId").asLong();
        String action = json.get("action").asText();

        String profile = normalizeConnectionProfile(clientSessionService.getProfile(session.getId()));
        String username = clientSessionService.getUsername(session.getId());

        boolean allowed = commandAuthorizationService.isAllowed(
                profile,
                action,
                username,
                pcId,
                json
        );

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
                    + ", username=" + username
                    + ", educationCode=" + (json.has("educationCode") ? json.get("educationCode").asText() : "none")
                    + ", supportCode=" + (json.has("supportCode") ? json.get("supportCode").asText() : "none")
                    + ", session=" + session.getId());

            return;
        }

        ObjectNode command = json.deepCopy();
        command.put("profile", profile);

        commandDispatchService.dispatch(pcId, command);

        System.out.println("✅ Command allowed: profile=" + profile
                + ", action=" + action
                + ", username=" + username
                + ", pcId=" + pcId);
    }

    public void broadcastFrame(Long pcId, String base64Image) {
        clientBroadcastService.broadcastFrame(pcId, base64Image);
    }

    public void broadcastBinaryFrame(Long pcId, byte[] imageData) {
        clientBroadcastService.broadcastBinaryFrame(pcId, imageData);
    }

    public void broadcastFileProgress(Long pcId, JsonNode progressJson) {
        clientBroadcastService.broadcastFileProgress(pcId, progressJson);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        clientSessionService.closeSession(session);
        remoteFileWebSocketService.removeOwnerSession(session);
        System.out.println("Web client disconnected: " + session.getId());
    }

    private String normalizeConnectionProfile(String profile) {
        if ("support_operator_view_client".equals(profile)) {
            return profile;
        }

        return sessionPermissionService.normalizeProfile(profile);
    }
}