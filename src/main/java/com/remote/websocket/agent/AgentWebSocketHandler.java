package com.remote.websocket.agent;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.remote.pc.model.Pc;
import com.remote.pc.repository.PcRepository;
import com.remote.websocket.agent.service.AgentSessionRegistry;
import com.remote.websocket.agent.service.AgentSessionService;
import com.remote.websocket.client.WebSocketClientHandler;
import com.remote.websocket.common.WebSocketMessageSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;

import static com.remote.common.ServerConstants.MESSAGE_FILE_PROGRESS;
import static com.remote.common.ServerConstants.MESSAGE_REMOTE_FILE_PREFIX;

@Slf4j
@Component
public class AgentWebSocketHandler extends TextWebSocketHandler {

    private final PcRepository pcRepository;
    private final WebSocketClientHandler webSocketClientHandler;
    private final AgentSessionRegistry agentSessionRegistry;
    private final AgentSessionService agentSessionService;
    private final WebSocketMessageSender webSocketMessageSender;
    private final ObjectMapper objectMapper;

    public AgentWebSocketHandler(
            PcRepository pcRepository,
            WebSocketClientHandler webSocketClientHandler,
            AgentSessionRegistry agentSessionRegistry,
            AgentSessionService agentSessionService,
            WebSocketMessageSender webSocketMessageSender,
            ObjectMapper objectMapper
    ) {
        this.pcRepository = pcRepository;
        this.webSocketClientHandler = webSocketClientHandler;
        this.agentSessionRegistry = agentSessionRegistry;
        this.agentSessionService = agentSessionService;
        this.webSocketMessageSender = webSocketMessageSender;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info(
                "Agent WebSocket connected: sessionId={}",
                session.getId()
        );
    }

    @Override
    protected void handleTextMessage(
            WebSocketSession session,
            TextMessage message
    ) throws IOException {
        String payload = message.getPayload();

        log.debug(
                "Agent text message received: sessionId={}, length={}",
                session.getId(),
                payload.length()
        );

        if (log.isTraceEnabled()) {
            if (payload.length() > 1000) {
                log.trace(
                        "Agent message payload preview: {}",
                        payload.substring(0, 100)
                );
            } else {
                log.trace(
                        "Agent message payload: {}",
                        payload
                );
            }
        }

        JsonNode json =
                objectMapper.readTree(payload);

        if (!json.has("type")) {
            log.warn(
                    "Agent message ignored because type is missing: sessionId={}",
                    session.getId()
            );
            return;
        }

        String type =
                json.get("type").asString();

        if (MESSAGE_FILE_PROGRESS.equals(type)) {
            Long pcId =
                    agentSessionRegistry.getPcIdBySession(session);

            if (pcId != null) {
                webSocketClientHandler.broadcastFileProgress(
                        pcId,
                        json
                );
            } else {
                log.warn(
                        "File progress ignored because PC id was not found for agent session: sessionId={}",
                        session.getId()
                );
            }

            return;
        }

        if (type.startsWith(MESSAGE_REMOTE_FILE_PREFIX)) {
            Long pcId =
                    agentSessionRegistry.getPcIdBySession(session);

            if (pcId != null) {
                webSocketClientHandler.forwardRemoteFileMessage(
                        json
                );
            } else {
                log.warn(
                        "Remote file message ignored because PC id was not found for agent session: sessionId={}, type={}",
                        session.getId(),
                        type
                );
            }

            return;
        }

        if ("register".equals(type)) {
            agentSessionService.register(
                    session,
                    json
            );

        } else if ("heartbeat".equals(type)) {
            agentSessionService.handleHeartbeat(
                    session
            );

        } else if ("frame".equals(type)) {
            handleFrame(
                    session,
                    json
            );

        } else {
            log.debug(
                    "Unknown agent message type ignored: sessionId={}, type={}",
                    session.getId(),
                    type
            );
        }
    }

    @Override
    protected void handleBinaryMessage(
            WebSocketSession session,
            BinaryMessage message
    ) {
        byte[] imageData =
                new byte[message.getPayload().remaining()];

        message.getPayload().get(imageData);

        String mac =
                agentSessionRegistry.getMacBySession(session);

        if (mac == null) {
            log.warn(
                    "Binary frame ignored because MAC was not found for session: sessionId={}",
                    session.getId()
            );
            return;
        }

        Pc pc =
                pcRepository.findByMacAddress(mac);

        if (pc == null) {
            log.warn(
                    "Binary frame ignored because PC was not found: mac={}",
                    mac
            );
            return;
        }

        webSocketClientHandler.broadcastBinaryFrame(
                pc.getId(),
                imageData
        );

        log.debug(
                "Binary frame forwarded: pcId={}, mac={}, sizeBytes={}",
                pc.getId(),
                mac,
                imageData.length
        );
    }

    private void handleFrame(
            WebSocketSession session,
            JsonNode json
    ) {
        String mac =
                agentSessionRegistry.getMacBySession(session);

        if (mac != null && json.has("image")) {
            String imageBase64 =
                    json.get("image").asString();

            log.debug(
                    "Text frame received from agent: sessionId={}, mac={}, sizeChars={}",
                    session.getId(),
                    mac,
                    imageBase64.length()
            );
        }
    }

    public void sendCommandToAgent(
            Long pcId,
            JsonNode command
    ) throws IOException {
        WebSocketSession agentSession =
                agentSessionRegistry.getByPcId(pcId);

        if (agentSession != null && agentSession.isOpen()) {
            String commandJson =
                    objectMapper.writeValueAsString(command);

            webSocketMessageSender.send(
                    agentSession,
                    new TextMessage(commandJson)
            );

            String action =
                    command.has("action")
                            ? command.get("action").asString()
                            : "unknown";

            log.info(
                    "Command forwarded to agent: pcId={}, action={}",
                    pcId,
                    action
            );

        } else {
            log.warn(
                    "Command was not sent because agent is not connected: pcId={}",
                    pcId
            );
        }
    }

    public void sendNotificationToAgent(
            Long pcId,
            String message
    ) throws IOException {
        WebSocketSession agentSession =
                agentSessionRegistry.getByPcId(pcId);

        if (agentSession != null && agentSession.isOpen()) {
            Map<String, String> notification = Map.of(
                    "type",
                    "notification",
                    "message",
                    message
            );

            String json =
                    objectMapper.writeValueAsString(notification);

            webSocketMessageSender.send(
                    agentSession,
                    new TextMessage(json)
            );

            log.info(
                    "Notification sent to agent: pcId={}",
                    pcId
            );

        } else {
            log.warn(
                    "Notification was not sent because agent is not connected: pcId={}",
                    pcId
            );
        }
    }

    @Override
    public void afterConnectionClosed(
            WebSocketSession session,
            CloseStatus status
    ) {
        agentSessionService.closeSession(session);

        log.info(
                "Agent WebSocket disconnected: sessionId={}, status={}",
                session.getId(),
                status
        );
    }
}