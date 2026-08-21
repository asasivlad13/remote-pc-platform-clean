package com.remote.websocket.agent.service;

import tools.jackson.databind.JsonNode;
import com.remote.auth.security.JwtUtil;
import com.remote.auth.service.DeviceCredentialService;
import com.remote.core.model.User;
import com.remote.core.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class AgentAuthenticationService {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final DeviceCredentialService deviceCredentialService;

    public AgentAuthenticationService(
            JwtUtil jwtUtil,
            UserRepository userRepository,
            DeviceCredentialService deviceCredentialService
    ) {
        this.jwtUtil =
                jwtUtil;

        this.userRepository =
                userRepository;

        this.deviceCredentialService =
                deviceCredentialService;
    }

    /*
     * Transitional agent authentication.
     *
     * Если сообщение содержит deviceCredential,
     * используется только device-auth.
     *
     * На legacy JWT отката в таком случае нет.
     */
    public Optional<AuthenticatedAgent> authenticate(
            JsonNode json,
            UUID installationId
    ) {
        if (json == null
                || installationId == null) {

            return Optional.empty();
        }

        if (json.has("deviceCredential")) {
            return authenticateWithDeviceCredential(
                    json,
                    installationId
            );
        }

        return authenticateWithLegacyJwt(
                json
        );
    }

    private Optional<AuthenticatedAgent> authenticateWithDeviceCredential(
            JsonNode json,
            UUID installationId
    ) {
        JsonNode credentialNode =
                json.get(
                        "deviceCredential"
                );

        if (credentialNode == null
                || credentialNode.isNull()) {

            return Optional.empty();
        }

        String rawCredential =
                credentialNode
                        .asString();

        Optional<DeviceCredentialService.AuthenticatedDevice>
                authenticatedDevice =
                deviceCredentialService
                        .authenticate(
                                installationId,
                                rawCredential
                        );

        if (authenticatedDevice.isEmpty()) {
            return Optional.empty();
        }

        DeviceCredentialService.AuthenticatedDevice device =
                authenticatedDevice.get();

        User user =
                userRepository
                        .findById(
                                device.userId()
                        )
                        .orElse(null);

        if (user == null
                || user.getId() == null
                || user.getEmail() == null
                || !Objects.equals(
                user.getEmail(),
                device.email()
        )
                || !canAuthenticateAgent(user)) {

            return Optional.empty();
        }

        return Optional.of(
                new AuthenticatedAgent(
                        user,
                        AgentAuthMode.DEVICE_CREDENTIAL,
                        device.pcId()
                )
        );
    }

    private Optional<AuthenticatedAgent> authenticateWithLegacyJwt(
            JsonNode json
    ) {
        if (!hasRequiredText(
                json,
                "token"
        )) {
            return Optional.empty();
        }

        String token =
                json.get("token")
                        .asString();

        if (!jwtUtil.validateToken(
                token
        )) {
            return Optional.empty();
        }

        try {
            /*
             * Агентский legacy JWT не содержит token_type.
             *
             * Новый browser session_access JWT намеренно
             * не разрешаем использовать как credential агента.
             */
            String tokenType =
                    jwtUtil.extractTokenType(
                            token
                    );

            if (tokenType != null) {
                return Optional.empty();
            }

            String email =
                    jwtUtil.extractUsername(
                            token
                    );

            if (email == null
                    || email.isBlank()) {

                return Optional.empty();
            }

            return userRepository
                    .findByEmail(
                            email
                    )
                    .filter(
                            this::canAuthenticateAgent
                    )
                    .map(
                            user ->
                                    new AuthenticatedAgent(
                                            user,
                                            AgentAuthMode.LEGACY_JWT,
                                            null
                                    )
                    );

        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private boolean canAuthenticateAgent(
            User user
    ) {
        return user.isEnabled()
                && user.isAccountNonLocked()
                && user.isAccountNonExpired()
                && user.isCredentialsNonExpired();
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

    public enum AgentAuthMode {

        LEGACY_JWT,

        DEVICE_CREDENTIAL
    }

    public record AuthenticatedAgent(
            User user,
            AgentAuthMode authMode,
            Long pcId
    ) {
    }
}