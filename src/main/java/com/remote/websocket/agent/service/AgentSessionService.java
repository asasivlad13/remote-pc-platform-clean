package com.remote.websocket.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.remote.auth.security.JwtUtil;
import com.remote.core.model.User;
import com.remote.core.repository.UserRepository;
import com.remote.pc.model.Pc;
import com.remote.pc.model.PcStatus;
import com.remote.pc.repository.PcRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;

@Slf4j
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

            log.info(
                    "Creating new PC record: mac={}, username={}",
                    mac,
                    username
            );
        } else {
            if (!pc.getName().equals(pcName)) {
                pc.setName(pcName);

                log.info(
                        "PC name updated: mac={}, pcName={}",
                        mac,
                        pcName
                );
            }

            if (pc.getUser() == null || !pc.getUser().getId().equals(user.getId())) {
                pc.setUser(user);

                log.info(
                        "PC reassigned to user: mac={}, username={}",
                        mac,
                        username
                );
            }
        }

        if (json.has("screenWidth") && json.has("screenHeight")) {
            pc.setScreenWidth(json.get("screenWidth").asInt());
            pc.setScreenHeight(json.get("screenHeight").asInt());

            log.debug(
                    "PC screen size updated: mac={}, width={}, height={}",
                    mac,
                    pc.getScreenWidth(),
                    pc.getScreenHeight()
            );
        }

        if (json.has("scaleX") && json.has("scaleY")) {
            double scaleX = json.get("scaleX").asDouble();
            double scaleY = json.get("scaleY").asDouble();

            log.debug(
                    "Agent scale factors received: mac={}, scaleX={}, scaleY={}",
                    mac,
                    scaleX,
                    scaleY
            );
        }

        if (json.has("webrtcUrl")) {
            pc.setWebrtcUrl(json.get("webrtcUrl").asText());

            log.debug(
                    "WebRTC URL updated: mac={}",
                    mac
            );
        }

        if (json.has("streamName")) {
            pc.setStreamName(json.get("streamName").asText());

            log.debug(
                    "Stream name updated: mac={}, streamName={}",
                    mac,
                    pc.getStreamName()
            );
        }

        pc.setStatus(PcStatus.ONLINE);
        pc.setLastConnection(LocalDateTime.now());

        Pc savedPc = pcRepository.save(pc);

        agentSessionRegistry.register(mac, savedPc.getId(), session);

        session.sendMessage(new TextMessage("{\"status\":\"registered\"}"));

        log.info(
                "Agent registered: pcId={}, pcName={}, mac={}, username={}",
                savedPc.getId(),
                pcName,
                mac,
                username
        );
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

        log.debug(
                "Agent heartbeat processed: mac={}, status={}",
                mac,
                pc.getStatus()
        );
    }

    public void closeSession(WebSocketSession session) {
        String mac = agentSessionRegistry.getMacBySession(session);

        if (mac != null) {
            Pc pc = pcRepository.findByMacAddress(mac);

            if (pc != null) {
                pc.setStatus(PcStatus.OFFLINE);
                pcRepository.save(pc);

                log.info(
                        "PC set to OFFLINE: pcId={}, pcName={}, mac={}",
                        pc.getId(),
                        pc.getName(),
                        mac
                );
            }
        }

        agentSessionRegistry.removeBySession(session);
    }
}