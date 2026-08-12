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
        viewers.computeIfPresent(pcId, (id, sessions) -> {
            sessions.remove(session);
            return sessions.isEmpty() ? null : sessions;
        });
    }

    public Set<WebSocketSession> getViewers(Long pcId) {
        return viewers.getOrDefault(pcId, Set.of());
    }

    public void removeSessionEverywhere(WebSocketSession session) {
        viewers.forEach((pcId, sessions) ->
                viewers.computeIfPresent(pcId, (id, currentSessions) -> {
                    currentSessions.remove(session);
                    return currentSessions.isEmpty() ? null : currentSessions;
                })
        );
    }
}