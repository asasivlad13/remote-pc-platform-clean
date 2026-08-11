package com.remote.websocket.client.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.remote.websocket.common.WebSocketMessageSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

@Slf4j
@Service
public class ClientBroadcastService {

    private final ClientViewerRegistry clientViewerRegistry;
    private final LastFrameCache lastFrameCache;
    private final WebSocketMessageSender webSocketMessageSender;

    public ClientBroadcastService(ClientViewerRegistry clientViewerRegistry,
                                  LastFrameCache lastFrameCache,
                                  WebSocketMessageSender webSocketMessageSender) {
        this.clientViewerRegistry = clientViewerRegistry;
        this.lastFrameCache = lastFrameCache;
        this.webSocketMessageSender = webSocketMessageSender;
    }

    public void broadcastFrame(Long pcId, String base64Image) {
        lastFrameCache.put(pcId, base64Image);

        for (WebSocketSession session : clientViewerRegistry.getViewers(pcId)) {
            if (session.isOpen()) {
                try {
                    webSocketMessageSender.send(
                            session,
                            new TextMessage(
                                    "{\"type\":\"frame\",\"image\":\""
                                            + base64Image
                                            + "\"}"
                            )
                    );
                } catch (IOException e) {
                    log.warn(
                            "Failed to send frame: pcId={}, sessionId={}",
                            pcId,
                            session.getId(),
                            e
                    );
                }
            }
        }
    }

    public void broadcastBinaryFrame(Long pcId, byte[] imageData) {
        for (WebSocketSession session : clientViewerRegistry.getViewers(pcId)) {
            if (session.isOpen()) {
                try {
                    webSocketMessageSender.send(
                            session,
                            new BinaryMessage(imageData)
                    );
                } catch (IOException e) {
                    log.warn(
                            "Failed to send binary frame: pcId={}, sessionId={}",
                            pcId,
                            session.getId(),
                            e
                    );
                }
            }
        }
    }

    public void broadcastFileProgress(Long pcId, JsonNode progressJson) {
        for (WebSocketSession session : clientViewerRegistry.getViewers(pcId)) {
            if (session.isOpen()) {
                try {
                    webSocketMessageSender.send(
                            session,
                            new TextMessage(progressJson.toString())
                    );
                } catch (IOException e) {
                    log.warn(
                            "Failed to send file progress: pcId={}, sessionId={}",
                            pcId,
                            session.getId(),
                            e
                    );
                }
            }
        }
    }
}