package com.remote.websocket.common;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

@Component
public class WebSocketMessageSender {

    public void send(WebSocketSession session,
                     WebSocketMessage<?> message) throws IOException {
        synchronized (session) {
            session.sendMessage(message);
        }
    }
}