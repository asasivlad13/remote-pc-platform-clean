package com.remote.websocket.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.remote.auth.security.JwtUtil;
import com.remote.core.model.User;
import com.remote.core.repository.UserRepository;
import com.remote.pc.model.Pc;
import com.remote.pc.model.PcStatus;
import com.remote.pc.repository.PcRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;

@Service
public class AgentSessionService {

    private final JwtUtil jwtUtil;
    private final PcRepository pcRepository;
    private final UserRepository userRepository;
    private final AgentSessionRegistry agentSessionRegistry;

    public AgentSessionService(JwtUtil jwtUtil,
                               PcRepository pcRepository,
                               UserRepository userRepository,
                               AgentSessionRegistry agentSessionRegistry) {
        this.jwtUtil = jwtUtil;
        this.pcRepository = pcRepository;
        this.userRepository = userRepository;
        this.agentSessionRegistry = agentSessionRegistry;
    }

    public void register(WebSocketSession session, JsonNode json) throws Exception {
        String token = json.get("token").asText();
        String pcName = json.get("pcName").asText();
        String mac = json.get("mac").asText();

        if (!jwtUtil.validateToken(token)) {
            session.sendMessage(new TextMessage("{\"error\":\"Invalid token\"}"));
            session.close();
            return;
        }

        String username = jwtUtil.extractUsername(token);

        User user = userRepository.findByUsername(username)
                .orElse(null);

        if (user == null) {
            session.sendMessage(new TextMessage("{\"error\":\"User not found\"}"));
            session.close();
            return;
        }

        Pc pc = pcRepository.findByMacAddress(mac);

        if (pc == null) {
            pc = new Pc();
            pc.setName(pcName);
            pc.setMacAddress(mac);
            pc.setUser(user);
            System.out.println("Creating new PC record for MAC: " + mac);
        } else {
            if (!pc.getName().equals(pcName)) {
                pc.setName(pcName);
                System.out.println("Updating PC name to '" + pcName + "'");
            }

            if (pc.getUser() == null || !pc.getUser().getId().equals(user.getId())) {
                pc.setUser(user);
                System.out.println("Re-assigning PC to user: " + username);
            }
        }

        if (json.has("screenWidth") && json.has("screenHeight")) {
            pc.setScreenWidth(json.get("screenWidth").asInt());
            pc.setScreenHeight(json.get("screenHeight").asInt());
            System.out.println("Screen size saved: " + pc.getScreenWidth() + "x" + pc.getScreenHeight());
        }

        if (json.has("scaleX") && json.has("scaleY")) {
            double scaleX = json.get("scaleX").asDouble();
            double scaleY = json.get("scaleY").asDouble();
            System.out.println("Scale factors: " + scaleX + " x " + scaleY);
        }

        if (json.has("webrtcUrl")) {
            pc.setWebrtcUrl(json.get("webrtcUrl").asText());
            System.out.println("WebRTC URL saved: " + pc.getWebrtcUrl());
        }

        if (json.has("streamName")) {
            pc.setStreamName(json.get("streamName").asText());
            System.out.println("Stream name saved: " + pc.getStreamName());
        }

        pc.setStatus(PcStatus.ONLINE);
        pc.setLastConnection(LocalDateTime.now());

        Pc savedPc = pcRepository.save(pc);

        agentSessionRegistry.register(mac, savedPc.getId(), session);

        session.sendMessage(new TextMessage("{\"status\":\"registered\"}"));

        System.out.println("Agent registered: " + pcName + " (" + mac + ") for user: " + username);
    }

    public void handleHeartbeat(WebSocketSession session) {
        String mac = agentSessionRegistry.getMacBySession(session);

        if (mac == null) {
            return;
        }

        Pc pc = pcRepository.findByMacAddress(mac);

        if (pc == null) {
            return;
        }

        pc.setLastConnection(LocalDateTime.now());

        if (pc.getStatus() != PcStatus.SLEEP) {
            pc.setStatus(PcStatus.ONLINE);
        }

        pcRepository.save(pc);
        System.out.println("Heartbeat from: " + mac);
    }

    public void closeSession(WebSocketSession session) {
        String mac = agentSessionRegistry.getMacBySession(session);

        if (mac != null) {
            Pc pc = pcRepository.findByMacAddress(mac);

            if (pc != null) {
                pc.setStatus(PcStatus.OFFLINE);
                pcRepository.save(pc);
                System.out.println("PC " + pc.getName() + " (" + mac + ") set to OFFLINE");
            }
        }

        agentSessionRegistry.removeBySession(session);
    }
}