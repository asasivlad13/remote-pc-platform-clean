package com.remote.websocket.agent.service;

import tools.jackson.databind.JsonNode;
import com.remote.core.model.User;
import com.remote.pc.model.Pc;
import com.remote.pc.model.PcConnectionStatus;
import com.remote.pc.repository.PcRepository;
import com.remote.websocket.common.WebSocketMessageSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
public class AgentSessionService {

    private static final int MAX_MAC_ADDRESS_LENGTH =
            50;

    private static final int MAX_DEVICE_NAME_LENGTH =
            255;

    private static final int MAX_OS_NAME_LENGTH =
            100;

    private static final int MAX_OS_VERSION_LENGTH =
            100;

    private static final int MAX_AGENT_VERSION_LENGTH =
            50;

    private final AgentAuthenticationService agentAuthenticationService;
    private final AgentPcRegistrationService agentPcRegistrationService;
    private final PcRepository pcRepository;
    private final AgentSessionRegistry agentSessionRegistry;
    private final WebSocketMessageSender webSocketMessageSender;

    public AgentSessionService(
            AgentAuthenticationService agentAuthenticationService,
            AgentPcRegistrationService agentPcRegistrationService,
            PcRepository pcRepository,
            AgentSessionRegistry agentSessionRegistry,
            WebSocketMessageSender webSocketMessageSender
    ) {
        this.agentAuthenticationService =
                agentAuthenticationService;

        this.agentPcRegistrationService =
                agentPcRegistrationService;

        this.pcRepository =
                pcRepository;

        this.agentSessionRegistry =
                agentSessionRegistry;

        this.webSocketMessageSender =
                webSocketMessageSender;
    }

    public void register(
            WebSocketSession session,
            JsonNode json
    ) throws IOException {

        if (!hasRequiredText(
                json,
                "pcName"
        )) {
            rejectRegistration(
                    session,
                    "PC name is missing"
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

        String pcName =
                json.get("pcName")
                        .asString();

        /*
         * MAC является optional metadata.
         *
         * Старые агенты продолжают присылать "mac".
         * Новые агенты смогут не передавать поле,
         * передавать null или пустую строку.
         */
        String mac =
                null;

        if (json.has("mac")
                && json.get("mac") != null
                && !json.get("mac")
                .isNull()) {

            String rawMac =
                    json.get("mac")
                            .asString()
                            .strip();

            if (!rawMac.isBlank()) {
                if (rawMac.length()
                        > MAX_MAC_ADDRESS_LENGTH) {

                    rejectRegistration(
                            session,
                            "MAC address is invalid"
                    );
                    return;
                }

                mac =
                        rawMac;
            }
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
                || !json.get("protocolVersion")
                .canConvertToInt()) {

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

        /*
         * Все структурные поля registration-message
         * проверены до security-auth.
         *
         * Благодаря этому malformed registration
         * не обновляет last_used_at device credential.
         */
        AgentAuthenticationService.AuthenticatedAgent
                authenticatedAgent =
                agentAuthenticationService
                        .authenticate(
                                json,
                                installationId
                        )
                        .orElse(null);

        if (authenticatedAgent == null) {
            rejectRegistration(
                    session,
                    "Invalid agent credentials"
            );
            return;
        }

        User user =
                authenticatedAgent.user();

        String email =
                user.getEmail();

        Integer screenWidth =
                null;

        Integer screenHeight =
                null;

        if (json.has("screenWidth")
                && json.has("screenHeight")) {

            screenWidth =
                    json.get("screenWidth")
                            .asInt();

            screenHeight =
                    json.get("screenHeight")
                            .asInt();

            log.debug(
                    "PC screen size received: installationId={}, width={}, height={}",
                    installationId,
                    screenWidth,
                    screenHeight
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

        boolean webrtcUrlPresent =
                json.has(
                        "webrtcUrl"
                );

        String webrtcUrl =
                webrtcUrlPresent
                        ? json.get("webrtcUrl")
                        .asString()
                        : null;

        boolean streamNamePresent =
                json.has(
                        "streamName"
                );

        String streamName =
                streamNamePresent
                        ? json.get("streamName")
                        .asString()
                        : null;

        AgentPcRegistrationService.RegistrationResult
                registrationResult =
                agentPcRegistrationService
                        .register(
                                authenticatedAgent,
                                new AgentPcRegistrationService
                                        .RegistrationData(
                                        installationId,
                                        pcName,
                                        mac,
                                        deviceName,
                                        osName,
                                        osVersion,
                                        agentVersion,
                                        protocolVersion,
                                        screenWidth,
                                        screenHeight,
                                        webrtcUrlPresent,
                                        webrtcUrl,
                                        streamNamePresent,
                                        streamName
                                )
                        );

        if (!registrationResult.isAccepted()) {
            rejectRegistration(
                    session,
                    registrationResult
                            .rejectionMessage()
            );
            return;
        }

        Long pcId =
                registrationResult.pcId();

        /*
         * DB transaction уже успешно завершена.
         *
         * Только теперь публикуем WebSocket-session
         * и сообщаем агенту, что регистрация завершена.
         */
        agentSessionRegistry.register(
                pcId,
                session
        );

        webSocketMessageSender.send(
                session,
                new TextMessage(
                        "{\"status\":\"registered\",\"pcId\":"
                                + pcId
                                + "}"
                )
        );

        log.info(
                "Agent registered: pcId={}, pcName={}, installationId={}, agentVersion={}, protocolVersion={}, email={}, authMode={}",
                pcId,
                pcName,
                installationId,
                agentVersion,
                protocolVersion,
                email,
                authenticatedAgent.authMode()
        );
    }

    public void handleHeartbeat(
            WebSocketSession session
    ) {
        Long pcId =
                agentSessionRegistry
                        .getPcIdBySession(
                                session
                        );

        if (pcId == null) {
            return;
        }

        Pc pc =
                pcRepository
                        .findById(
                                pcId
                        )
                        .orElse(null);

        if (pc == null) {
            return;
        }

        pc.setLastSeenAt(
                Instant.now()
        );

        /*
         * Heartbeat означает, что агент подключён,
         * даже если ПК находится в SOFT_SLEEP.
         */
        pc.setConnectionStatus(
                PcConnectionStatus.ONLINE
        );

        pcRepository.save(
                pc
        );

        log.debug(
                "Agent heartbeat processed: pcId={}, connectionStatus={}, powerState={}",
                pc.getId(),
                pc.getConnectionStatus(),
                pc.getPowerState()
        );
    }

    public void closeSession(
            WebSocketSession session
    ) {
        Long pcId =
                agentSessionRegistry
                        .getPcIdBySession(
                                session
                        );

        if (pcId != null) {
            Pc pc =
                    pcRepository
                            .findById(
                                    pcId
                            )
                            .orElse(null);

            if (pc != null) {
                /*
                 * Отключение WebSocket не меняет
                 * последнее известное powerState.
                 */
                pc.setConnectionStatus(
                        PcConnectionStatus.OFFLINE
                );

                pcRepository.save(
                        pc
                );

                log.info(
                        "PC set to OFFLINE: pcId={}, pcName={}",
                        pc.getId(),
                        pc.getName()
                );
            }
        }

        agentSessionRegistry
                .removeBySession(
                        session
                );
    }

    private boolean hasRequiredText(
            JsonNode json,
            String fieldName
    ) {
        return json.has(fieldName)
                && json.get(fieldName) != null
                && !json.get(fieldName)
                .isNull()
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

        if (value.length()
                > maxLength) {

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