package com.remote.websocket.client.service;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ClientViewerRegistry {

    private final ConcurrentHashMap<Long, Set<WebSocketSession>> viewers = new ConcurrentHashMap<>();

    public void addViewer(Long pcId, WebSocketSession session) {
        viewers.computeIfAbsent(pcId, k -> ConcurrentHashMap.newKeySet())
                .add(session);
    }

    public void removeViewer(Long pcId, WebSocketSession session) {
        Set<WebSocketSession> sessions = viewers.get(pcId);

        if (sessions != null) {
            sessions.remove(session);

            if (sessions.isEmpty()) {
                viewers.remove(pcId);
            }
        }
    }

    public Set<WebSocketSession> getViewers(Long pcId) {
        return viewers.getOrDefault(pcId, Set.of());
    }

    public void removeSessionEverywhere(WebSocketSession session) {
        viewers.values().forEach(set -> set.remove(session));
        viewers.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }
}