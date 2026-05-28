package com.remote.websocket.client.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Service
public class ClientBroadcastService {

    private final ClientViewerRegistry clientViewerRegistry;
    private final LastFrameCache lastFrameCache;

    public ClientBroadcastService(ClientViewerRegistry clientViewerRegistry,
                                  LastFrameCache lastFrameCache) {
        this.clientViewerRegistry = clientViewerRegistry;
        this.lastFrameCache = lastFrameCache;
    }

    public void broadcastFrame(Long pcId, String base64Image) {
        lastFrameCache.put(pcId, base64Image);

        for (WebSocketSession session : clientViewerRegistry.getViewers(pcId)) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(
                            new TextMessage(
                                    "{\"type\":\"frame\",\"image\":\"" + base64Image + "\"}"
                            )
                    );
                } catch (Exception e) {
                    System.err.println("Error sending frame: " + e.getMessage());
                }
            }
        }
    }

    public void broadcastBinaryFrame(Long pcId, byte[] imageData) {
        for (WebSocketSession session : clientViewerRegistry.getViewers(pcId)) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new BinaryMessage(imageData));
                } catch (Exception e) {
                    System.err.println("Error sending binary frame: " + e.getMessage());
                }
            }
        }
    }

    public void broadcastFileProgress(Long pcId, JsonNode progressJson) {
        for (WebSocketSession session : clientViewerRegistry.getViewers(pcId)) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(progressJson.toString()));
                } catch (Exception e) {
                    System.err.println("Error sending file progress: " + e.getMessage());
                }
            }
        }
    }
}