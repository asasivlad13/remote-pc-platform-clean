package com.remote.websocket.agent.service;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AgentSessionRegistry {

    private final Map<String, WebSocketSession> sessionsByMac = new ConcurrentHashMap<>();
    private final Map<Long, WebSocketSession> sessionsByPcId = new ConcurrentHashMap<>();

    public void register(String macAddress, Long pcId, WebSocketSession session) {
        sessionsByMac.put(macAddress, session);
        sessionsByPcId.put(pcId, session);
    }

    public WebSocketSession getByMac(String macAddress) {
        return sessionsByMac.get(macAddress);
    }

    public WebSocketSession getByPcId(Long pcId) {
        return sessionsByPcId.get(pcId);
    }

    public Long getPcIdBySession(WebSocketSession session) {
        for (Map.Entry<Long, WebSocketSession> entry : sessionsByPcId.entrySet()) {
            if (entry.getValue().getId().equals(session.getId())) {
                return entry.getKey();
            }
        }

        return null;
    }

    public String getMacBySession(WebSocketSession session) {
        for (Map.Entry<String, WebSocketSession> entry : sessionsByMac.entrySet()) {
            if (entry.getValue().equals(session)) {
                return entry.getKey();
            }
        }

        return null;
    }

    public void removeBySession(WebSocketSession session) {
        sessionsByMac.entrySet().removeIf(entry -> entry.getValue().equals(session));
        sessionsByPcId.entrySet().removeIf(entry -> entry.getValue().equals(session));
    }
}