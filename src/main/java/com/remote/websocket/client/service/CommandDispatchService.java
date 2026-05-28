package com.remote.websocket.client.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.remote.websocket.agent.AgentWebSocketHandler;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
public class CommandDispatchService {

    private final AgentWebSocketHandler agentWebSocketHandler;

    public CommandDispatchService(@Lazy AgentWebSocketHandler agentWebSocketHandler) {
        this.agentWebSocketHandler = agentWebSocketHandler;
    }

    public void dispatch(Long pcId, ObjectNode command) throws Exception {
        agentWebSocketHandler.sendCommandToAgent(pcId, command);
    }

    public void sendSettings(Long pcId, JsonNode json) throws Exception {
        agentWebSocketHandler.sendCommandToAgent(pcId, json);
    }
}