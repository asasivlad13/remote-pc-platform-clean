package com.remote.websocket.agent.service;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AgentSessionRegistry {

    private final Map<Long, WebSocketSession> sessionsByPcId =
            new ConcurrentHashMap<>();

    private final Map<String, Long> pcIdsBySessionId =
            new ConcurrentHashMap<>();

    public void register(
            Long pcId,
            WebSocketSession session
    ) {
        WebSocketSession previousSession =
                sessionsByPcId.put(
                        pcId,
                        session
                );

        if (previousSession != null
                && !previousSession.getId()
                .equals(session.getId())) {

            pcIdsBySessionId.remove(
                    previousSession.getId(),
                    pcId
            );
        }

        Long previousPcId =
                pcIdsBySessionId.put(
                        session.getId(),
                        pcId
                );

        if (previousPcId != null
                && !previousPcId.equals(pcId)) {

            sessionsByPcId.remove(
                    previousPcId,
                    session
            );
        }
    }

    public WebSocketSession getByPcId(
            Long pcId
    ) {
        return sessionsByPcId.get(pcId);
    }

    public Long getPcIdBySession(
            WebSocketSession session
    ) {
        if (session == null) {
            return null;
        }

        return pcIdsBySessionId.get(
                session.getId()
        );
    }

    public void removeBySession(
            WebSocketSession session
    ) {
        if (session == null) {
            return;
        }

        Long pcId =
                pcIdsBySessionId.remove(
                        session.getId()
                );

        if (pcId != null) {
            sessionsByPcId.remove(
                    pcId,
                    session
            );
        }
    }
}