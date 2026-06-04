package com.remote.core.config;

import com.remote.websocket.agent.AgentWebSocketHandler;
import com.remote.websocket.client.WebSocketClientHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final AgentWebSocketHandler agentWebSocketHandler;
    private final WebSocketClientHandler webSocketClientHandler;

    public WebSocketConfig(AgentWebSocketHandler agentWebSocketHandler,
                           WebSocketClientHandler webSocketClientHandler) {
        this.agentWebSocketHandler = agentWebSocketHandler;
        this.webSocketClientHandler = webSocketClientHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(agentWebSocketHandler, "/ws/agent")
                .setAllowedOrigins("*");

        registry.addHandler(webSocketClientHandler, "/ws/client")
                .setAllowedOrigins("*");
    }
}