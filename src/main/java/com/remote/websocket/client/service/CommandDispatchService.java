package com.remote.websocket.client.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import com.remote.websocket.agent.AgentWebSocketHandler;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class CommandDispatchService {

    private final AgentWebSocketHandler agentWebSocketHandler;

    public CommandDispatchService(@Lazy AgentWebSocketHandler agentWebSocketHandler) {
        this.agentWebSocketHandler = agentWebSocketHandler;
    }

    public void dispatch(Long pcId, ObjectNode command) throws IOException {
        agentWebSocketHandler.sendCommandToAgent(pcId, command);
    }

    public void sendSettings(Long pcId, JsonNode json) throws IOException {
        agentWebSocketHandler.sendCommandToAgent(pcId, json);
    }
}