package com.remote.websocket.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remote.auth.security.JwtUtil;
import com.remote.core.repository.UserRepository;
import com.remote.pc.model.Pc;
import com.remote.pc.repository.PcRepository;
import com.remote.websocket.agent.service.AgentSessionRegistry;
import com.remote.websocket.client.WebSocketClientHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import com.remote.websocket.agent.service.AgentSessionService;

import java.util.Map;

@Component
public class AgentWebSocketHandler extends TextWebSocketHandler {

    private final JwtUtil jwtUtil;
    private final PcRepository pcRepository;
    private final UserRepository userRepository;
    private final WebSocketClientHandler webSocketClientHandler;
    private final AgentSessionRegistry agentSessionRegistry;
    private final AgentSessionService agentSessionService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentWebSocketHandler(JwtUtil jwtUtil,
                                 PcRepository pcRepository,
                                 UserRepository userRepository,
                                 WebSocketClientHandler webSocketClientHandler,
                                 AgentSessionRegistry agentSessionRegistry, AgentSessionService agentSessionService) {
        this.jwtUtil = jwtUtil;
        this.pcRepository = pcRepository;
        this.userRepository = userRepository;
        this.webSocketClientHandler = webSocketClientHandler;
        this.agentSessionRegistry = agentSessionRegistry;
        this.agentSessionService = agentSessionService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        System.out.println("Agent connected: " + session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        System.out.println("Received text, length: " + payload.length() + " chars");

        if (payload.length() > 1000) {
            System.out.println("  (first 100 chars): " + payload.substring(0, 100) + "...");
        } else {
            System.out.println("  content: " + payload);
        }

        JsonNode json = objectMapper.readTree(payload);

        if (!json.has("type")) {
            return;
        }

        String type = json.get("type").asText();

        if ("FILE_PROGRESS".equals(type)) {
            Long pcId = agentSessionRegistry.getPcIdBySession(session);

            if (pcId != null) {
                webSocketClientHandler.broadcastFileProgress(pcId, json);
            }

            return;
        }

        if (type.startsWith("REMOTE_FILE_")) {
            Long pcId = agentSessionRegistry.getPcIdBySession(session);

            if (pcId != null) {
                webSocketClientHandler.forwardRemoteFileMessage(pcId, json);
            }

            return;
        }

        if ("register".equals(type)) {
            agentSessionService.register(session, json);
        } else if ("heartbeat".equals(type)) {
            agentSessionService.handleHeartbeat(session);
        } else if ("frame".equals(type)) {
            handleFrame(session, json);
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        byte[] imageData = new byte[message.getPayload().remaining()];
        message.getPayload().get(imageData);

        String mac = agentSessionRegistry.getMacBySession(session);

        if (mac != null) {
            Pc pc = pcRepository.findByMacAddress(mac);

            if (pc != null) {
                webSocketClientHandler.broadcastBinaryFrame(pc.getId(), imageData);
                System.out.println("📸 Binary frame (" + imageData.length + " bytes) from " + mac);
            }
        }
    }

    private void handleFrame(WebSocketSession session, JsonNode json) {
        String mac = agentSessionRegistry.getMacBySession(session);

        if (mac != null && json.has("image")) {
            String imageBase64 = json.get("image").asText();
            System.out.println("📸 Frame from " + mac + ", size: " + imageBase64.length() + " chars");
        }
    }

    public void sendCommandToAgent(Long pcId, JsonNode command) throws Exception {
        WebSocketSession agentSession = agentSessionRegistry.getByPcId(pcId);

        if (agentSession != null && agentSession.isOpen()) {
            String commandJson = objectMapper.writeValueAsString(command);
            agentSession.sendMessage(new TextMessage(commandJson));
            System.out.println("Command forwarded to agent for PC " + pcId + ": " + commandJson);
        } else {
            System.out.println("Agent not connected for PC " + pcId);
        }
    }

    public void sendNotificationToAgent(Long pcId, String message) throws Exception {
        WebSocketSession agentSession = agentSessionRegistry.getByPcId(pcId);

        if (agentSession != null && agentSession.isOpen()) {
            Map<String, String> notification = Map.of(
                    "type", "notification",
                    "message", message
            );

            String json = objectMapper.writeValueAsString(notification);

            agentSession.sendMessage(new TextMessage(json));
            System.out.println("📢 Notification sent to agent for PC " + pcId + ": " + json);
        } else {
            System.out.println("Agent not connected for PC " + pcId + ", cannot send notification");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        agentSessionService.closeSession(session);
        System.out.println("Agent disconnected: " + session.getId());
    }
}