package com.remote.websocket.agent.service;

import tools.jackson.databind.JsonNode;
import com.remote.auth.security.JwtUtil;
import com.remote.core.model.User;
import com.remote.core.repository.UserRepository;
import com.remote.pc.model.Pc;
import com.remote.pc.model.PcStatus;
import com.remote.pc.repository.PcRepository;
import com.remote.websocket.common.WebSocketMessageSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
public class AgentSessionService {

    private static final int MAX_DEVICE_NAME_LENGTH = 255;
    private static final int MAX_OS_NAME_LENGTH = 100;
    private static final int MAX_OS_VERSION_LENGTH = 100;
    private static final int MAX_AGENT_VERSION_LENGTH = 50;

    private final JwtUtil jwtUtil;
    private final PcRepository pcRepository;
    private final UserRepository userRepository;
    private final AgentSessionRegistry agentSessionRegistry;
    private final WebSocketMessageSender webSocketMessageSender;

    public AgentSessionService(
            JwtUtil jwtUtil,
            PcRepository pcRepository,
            UserRepository userRepository,
            AgentSessionRegistry agentSessionRegistry,
            WebSocketMessageSender webSocketMessageSender
    ) {
        this.jwtUtil = jwtUtil;
        this.pcRepository = pcRepository;
        this.userRepository = userRepository;
        this.agentSessionRegistry = agentSessionRegistry;
        this.webSocketMessageSender = webSocketMessageSender;
    }

    public void register(
            WebSocketSession session,
            JsonNode json
    ) throws IOException {

        if (!hasRequiredText(json, "token")) {
            rejectRegistration(
                    session,
                    "Token is missing"
            );
            return;
        }

        if (!hasRequiredText(json, "pcName")) {
            rejectRegistration(
                    session,
                    "PC name is missing"
            );
            return;
        }

        if (!hasRequiredText(json, "mac")) {
            rejectRegistration(
                    session,
                    "MAC address is missing"
            );
            return;
        }

        String token =
                json.get("token").asString();

        String pcName =
                json.get("pcName").asString();

        String mac =
                json.get("mac").asString();

        if (!jwtUtil.validateToken(token)) {
            rejectRegistration(
                    session,
                    "Invalid token"
            );
            return;
        }

        if (!hasRequiredText(
                json,
                "installationId"
        )) {
            rejectRegistration(
                    session,
                    "Installation id is missing"
            );
            return;
        }

        UUID installationId;

        try {
            installationId =
                    UUID.fromString(
                            json.get("installationId")
                                    .asString()
                    );

        } catch (IllegalArgumentException e) {
            rejectRegistration(
                    session,
                    "Invalid installation id"
            );
            return;
        }

        String deviceName =
                readRequiredText(
                        json,
                        "deviceName",
                        MAX_DEVICE_NAME_LENGTH
                );

        String osName =
                readRequiredText(
                        json,
                        "osName",
                        MAX_OS_NAME_LENGTH
                );

        String osVersion =
                readRequiredText(
                        json,
                        "osVersion",
                        MAX_OS_VERSION_LENGTH
                );

        String agentVersion =
                readRequiredText(
                        json,
                        "agentVersion",
                        MAX_AGENT_VERSION_LENGTH
                );

        if (deviceName == null
                || osName == null
                || osVersion == null
                || agentVersion == null) {

            rejectRegistration(
                    session,
                    "Invalid device metadata"
            );
            return;
        }

        if (!json.has("protocolVersion")
                || !json.get("protocolVersion").canConvertToInt()) {

            rejectRegistration(
                    session,
                    "Protocol version is missing or invalid"
            );
            return;
        }

        int protocolVersion =
                json.get("protocolVersion")
                        .asInt();

        if (protocolVersion <= 0) {
            rejectRegistration(
                    session,
                    "Protocol version is invalid"
            );
            return;
        }

        String email =
                jwtUtil.extractUsername(token);

        User user =
                userRepository.findByEmail(email)
                        .orElse(null);

        if (user == null) {
            rejectRegistration(
                    session,
                    "User not found"
            );
            return;
        }

        Pc pc =
                pcRepository
                        .findByInstallationId(
                                installationId
                        )
                        .orElse(null);

        if (pc == null) {
            pc = new Pc();

            pc.setInstallationId(
                    installationId
            );

            pc.setName(pcName);
            pc.setMacAddress(mac);
            pc.setUser(user);

            log.info(
                    "Creating new PC record: installationId={}, mac={}, email={}",
                    installationId,
                    mac,
                    email
            );

        } else {
            if (pc.getUser() == null
                    || !pc.getUser()
                    .getId()
                    .equals(user.getId())) {

                log.warn(
                        "Agent registration rejected because installation belongs to another user: installationId={}, requestedEmail={}",
                        installationId,
                        email
                );

                rejectRegistration(
                        session,
                        "Installation belongs to another user"
                );
                return;
            }

            if (!Objects.equals(
                    pc.getName(),
                    pcName
            )) {
                pc.setName(pcName);

                log.info(
                        "PC name updated: installationId={}, pcName={}",
                        installationId,
                        pcName
                );
            }

            if (!Objects.equals(
                    pc.getMacAddress(),
                    mac
            )) {
                String previousMac =
                        pc.getMacAddress();

                pc.setMacAddress(mac);

                log.info(
                        "PC MAC address updated: installationId={}, oldMac={}, newMac={}",
                        installationId,
                        previousMac,
                        mac
                );
            }
        }

        pc.setDeviceName(deviceName);
        pc.setOsName(osName);
        pc.setOsVersion(osVersion);
        pc.setAgentVersion(agentVersion);
        pc.setProtocolVersion(protocolVersion);

        if (json.has("screenWidth")
                && json.has("screenHeight")) {

            pc.setScreenWidth(
                    json.get("screenWidth")
                            .asInt()
            );

            pc.setScreenHeight(
                    json.get("screenHeight")
                            .asInt()
            );

            log.debug(
                    "PC screen size updated: installationId={}, width={}, height={}",
                    installationId,
                    pc.getScreenWidth(),
                    pc.getScreenHeight()
            );
        }

        if (json.has("scaleX")
                && json.has("scaleY")) {

            double scaleX =
                    json.get("scaleX")
                            .asDouble();

            double scaleY =
                    json.get("scaleY")
                            .asDouble();

            log.debug(
                    "Agent scale factors received: installationId={}, scaleX={}, scaleY={}",
                    installationId,
                    scaleX,
                    scaleY
            );
        }

        if (json.has("webrtcUrl")) {
            pc.setWebrtcUrl(
                    json.get("webrtcUrl")
                            .asString()
            );
        }

        if (json.has("streamName")) {
            pc.setStreamName(
                    json.get("streamName")
                            .asString()
            );
        }

        pc.setStatus(
                PcStatus.ONLINE
        );

        pc.setLastSeenAt(
                Instant.now()
        );

        Pc savedPc =
                pcRepository.save(pc);

        agentSessionRegistry.register(
                savedPc.getId(),
                session
        );

        webSocketMessageSender.send(
                session,
                new TextMessage(
                        "{\"status\":\"registered\"}"
                )
        );

        log.info(
                "Agent registered: pcId={}, pcName={}, installationId={}, agentVersion={}, protocolVersion={}, email={}",
                savedPc.getId(),
                pcName,
                installationId,
                agentVersion,
                protocolVersion,
                email
        );
    }

    public void handleHeartbeat(
            WebSocketSession session
    ) {
        Long pcId =
                agentSessionRegistry
                        .getPcIdBySession(session);

        if (pcId == null) {
            return;
        }

        Pc pc =
                pcRepository
                        .findById(pcId)
                        .orElse(null);

        if (pc == null) {
            return;
        }

        pc.setLastSeenAt(
                Instant.now()
        );

        if (pc.getStatus()
                != PcStatus.SLEEP) {

            pc.setStatus(
                    PcStatus.ONLINE
            );
        }

        pcRepository.save(pc);

        log.debug(
                "Agent heartbeat processed: pcId={}, status={}",
                pc.getId(),
                pc.getStatus()
        );
    }

    public void closeSession(
            WebSocketSession session
    ) {
        Long pcId =
                agentSessionRegistry
                        .getPcIdBySession(session);

        if (pcId != null) {
            Pc pc =
                    pcRepository
                            .findById(pcId)
                            .orElse(null);

            if (pc != null) {
                pc.setStatus(
                        PcStatus.OFFLINE
                );

                pcRepository.save(pc);

                log.info(
                        "PC set to OFFLINE: pcId={}, pcName={}",
                        pc.getId(),
                        pc.getName()
                );
            }
        }

        agentSessionRegistry
                .removeBySession(session);
    }

    private boolean hasRequiredText(
            JsonNode json,
            String fieldName
    ) {
        return json.has(fieldName)
                && json.get(fieldName) != null
                && !json.get(fieldName).isNull()
                && !json.get(fieldName)
                .asString()
                .isBlank();
    }

    private String readRequiredText(
            JsonNode json,
            String fieldName,
            int maxLength
    ) {
        if (!hasRequiredText(
                json,
                fieldName
        )) {
            return null;
        }

        String value =
                json.get(fieldName)
                        .asString()
                        .strip();

        if (value.length() > maxLength) {
            return null;
        }

        return value;
    }

    private void rejectRegistration(
            WebSocketSession session,
            String message
    ) throws IOException {

        webSocketMessageSender.send(
                session,
                new TextMessage(
                        "{\"error\":\""
                                + message
                                + "\"}"
                )
        );

        session.close();
    }
}