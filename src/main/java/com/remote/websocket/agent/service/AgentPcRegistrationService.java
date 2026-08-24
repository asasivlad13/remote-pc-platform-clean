package com.remote.websocket.agent.service;

import com.remote.core.model.User;
import com.remote.pc.model.Pc;
import com.remote.pc.model.PcConnectionStatus;
import com.remote.pc.repository.PcRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
public class AgentPcRegistrationService {

    private final PcRepository pcRepository;

    public AgentPcRegistrationService(
            PcRepository pcRepository
    ) {
        this.pcRepository =
                pcRepository;
    }

    /*
     * Вся DB-часть регистрации агента находится
     * внутри одной транзакции.
     *
     * WebSocket registry и отправка ответа сервером
     * выполняются уже после возврата из этого метода,
     * то есть после успешного commit.
     */
    @Transactional
    public RegistrationResult register(
            AgentAuthenticationService.AuthenticatedAgent
                    authenticatedAgent,
            RegistrationData registrationData
    ) {
        if (authenticatedAgent == null
                || registrationData == null) {

            throw new IllegalArgumentException(
                    "Authenticated agent and registration data are required"
            );
        }

        User user =
                authenticatedAgent.user();

        if (user == null
                || user.getId() == null) {

            throw new IllegalArgumentException(
                    "Authenticated user id is required"
            );
        }

        Long userId =
                user.getId();

        String email =
                user.getEmail();

        Instant now =
                Instant.now();

        /*
         * Только legacy bootstrap имеет право
         * создать новую установку.
         *
         * ON CONFLICT защищает от гонки:
         *
         * request A -> INSERT
         * request B -> INSERT ON CONFLICT DO NOTHING
         *
         * После этого оба запроса работают
         * с одной строкой Pc.
         */
        if (authenticatedAgent.authMode()
                == AgentAuthenticationService
                .AgentAuthMode
                .LEGACY_JWT) {

            int inserted =
                    pcRepository
                            .insertBootstrapIfAbsent(
                                    registrationData
                                            .installationId(),
                                    registrationData
                                            .pcName(),
                                    registrationData
                                            .deviceName(),
                                    registrationData
                                            .mac(),
                                    registrationData
                                            .osName(),
                                    registrationData
                                            .osVersion(),
                                    registrationData
                                            .agentVersion(),
                                    registrationData
                                            .protocolVersion(),
                                    now,
                                    userId,
                                    now,
                                    now
                            );

            if (inserted == 1) {
                log.info(
                        "Created new PC record: installationId={}, mac={}, email={}, authMode={}",
                        registrationData.installationId(),
                        registrationData.mac(),
                        email,
                        authenticatedAgent.authMode()
                );
            }
        }

        /*
         * Строка либо уже существовала,
         * либо только что была создана.
         *
         * PESSIMISTIC_WRITE сериализует дальнейшую
         * регистрацию одного installationId.
         */
        Pc pc =
                pcRepository
                        .findByInstallationIdForUpdate(
                                registrationData
                                        .installationId()
                        )
                        .orElse(null);

        if (pc == null) {
            /*
             * Device credential не имеет права
             * создавать неизвестную установку.
             */
            if (authenticatedAgent.authMode()
                    == AgentAuthenticationService
                    .AgentAuthMode
                    .DEVICE_CREDENTIAL) {

                return RegistrationResult.rejected(
                        "Device installation not found"
                );
            }

            /*
             * Для legacy это уже внутренняя ошибка:
             * INSERT bootstrap должен был либо создать
             * строку, либо встретить существующую.
             */
            throw new IllegalStateException(
                    "PC bootstrap did not produce installation record"
            );
        }

        if (pc.getUser() == null
                || pc.getUser()
                .getId() == null
                || !Objects.equals(
                pc.getUser().getId(),
                userId
        )) {

            log.warn(
                    "Agent registration rejected because installation belongs to another user: installationId={}, requestedEmail={}, authMode={}",
                    registrationData.installationId(),
                    email,
                    authenticatedAgent.authMode()
            );

            return RegistrationResult.rejected(
                    "Installation belongs to another user"
            );
        }

        /*
         * Device credential должен принадлежать
         * именно этой записи Pc.
         */
        if (authenticatedAgent.authMode()
                == AgentAuthenticationService
                .AgentAuthMode
                .DEVICE_CREDENTIAL

                && !Objects.equals(
                pc.getId(),
                authenticatedAgent.pcId()
        )) {

            log.warn(
                    "Agent registration rejected because device credential does not match PC: installationId={}, pcId={}, credentialPcId={}",
                    registrationData.installationId(),
                    pc.getId(),
                    authenticatedAgent.pcId()
            );

            return RegistrationResult.rejected(
                    "Device credential does not match installation"
            );
        }

        if (!Objects.equals(
                pc.getName(),
                registrationData.pcName()
        )) {
            pc.setName(
                    registrationData.pcName()
            );

            log.info(
                    "PC name updated: installationId={}, pcName={}",
                    registrationData.installationId(),
                    registrationData.pcName()
            );
        }

        if (!Objects.equals(
                pc.getMacAddress(),
                registrationData.mac()
        )) {
            String previousMac =
                    pc.getMacAddress();

            pc.setMacAddress(
                    registrationData.mac()
            );

            log.info(
                    "PC MAC address updated: installationId={}, oldMac={}, newMac={}",
                    registrationData.installationId(),
                    previousMac,
                    registrationData.mac()
            );
        }

        pc.setDeviceName(
                registrationData.deviceName()
        );

        pc.setOsName(
                registrationData.osName()
        );

        pc.setOsVersion(
                registrationData.osVersion()
        );

        pc.setAgentVersion(
                registrationData.agentVersion()
        );

        pc.setProtocolVersion(
                registrationData.protocolVersion()
        );

        if (registrationData.screenWidth() != null
                && registrationData.screenHeight() != null) {

            pc.setScreenWidth(
                    registrationData.screenWidth()
            );

            pc.setScreenHeight(
                    registrationData.screenHeight()
            );
        }

        if (registrationData.webrtcUrlPresent()) {
            pc.setWebrtcUrl(
                    registrationData.webrtcUrl()
            );
        }

        if (registrationData.streamNamePresent()) {
            pc.setStreamName(
                    registrationData.streamName()
            );
        }

        /*
         * REGISTER означает только наличие
         * активного соединения агента.
         *
         * powerState намеренно не меняется
         * для уже существующей записи Pc.
         */
        pc.setConnectionStatus(
                PcConnectionStatus.ONLINE
        );

        pc.setLastSeenAt(
                now
        );

        /*
         * flush выполняется до выхода из service-метода.
         *
         * Сам transaction commit произойдёт при выходе
         * через Spring proxy до возврата управления
         * AgentSessionService.
         */
        Pc savedPc =
                pcRepository
                        .saveAndFlush(
                                pc
                        );

        return RegistrationResult.accepted(
                savedPc.getId()
        );
    }

    public record RegistrationData(
            UUID installationId,
            String pcName,
            String mac,
            String deviceName,
            String osName,
            String osVersion,
            String agentVersion,
            Integer protocolVersion,
            Integer screenWidth,
            Integer screenHeight,
            boolean webrtcUrlPresent,
            String webrtcUrl,
            boolean streamNamePresent,
            String streamName
    ) {
    }

    public record RegistrationResult(
            Long pcId,
            String rejectionMessage
    ) {

        public static RegistrationResult accepted(
                Long pcId
        ) {
            if (pcId == null) {
                throw new IllegalArgumentException(
                        "Registered PC id is required"
                );
            }

            return new RegistrationResult(
                    pcId,
                    null
            );
        }

        public static RegistrationResult rejected(
                String message
        ) {
            if (message == null
                    || message.isBlank()) {

                throw new IllegalArgumentException(
                        "Registration rejection message is required"
                );
            }

            return new RegistrationResult(
                    null,
                    message
            );
        }

        public boolean isAccepted() {
            return pcId != null
                    && rejectionMessage == null;
        }
    }
}