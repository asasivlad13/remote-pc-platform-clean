package com.remote.auth.service;

import com.remote.auth.dto.DeviceCredentialResponse;
import com.remote.auth.model.DeviceCredentialRevokeReason;
import com.remote.core.model.User;
import com.remote.core.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DeviceCredentialManagementService {

    private final DeviceCredentialService deviceCredentialService;
    private final UserRepository userRepository;

    public DeviceCredentialManagementService(
            DeviceCredentialService deviceCredentialService,
            UserRepository userRepository
    ) {
        this.deviceCredentialService =
                deviceCredentialService;

        this.userRepository =
                userRepository;
    }

    public DeviceCredentialResponse issueCredential(
            Long pcId,
            String authenticatedEmail
    ) {
        User user =
                requireAuthenticatedUser(
                        authenticatedEmail
                );

        DeviceCredentialService.IssuedDeviceCredential issued =
                deviceCredentialService
                        .issueCredential(
                                pcId,
                                user.getId()
                        );

        return toResponse(
                issued
        );
    }

    public DeviceCredentialResponse rotateCredential(
            Long pcId,
            String authenticatedEmail
    ) {
        User user =
                requireAuthenticatedUser(
                        authenticatedEmail
                );

        DeviceCredentialService.IssuedDeviceCredential issued =
                deviceCredentialService
                        .rotateCredential(
                                pcId,
                                user.getId()
                        );

        return toResponse(
                issued
        );
    }

    public void revokeCredential(
            Long pcId,
            String authenticatedEmail
    ) {
        User user =
                requireAuthenticatedUser(
                        authenticatedEmail
                );

        deviceCredentialService
                .revokeCredential(
                        pcId,
                        user.getId(),
                        DeviceCredentialRevokeReason.USER_REVOKED
                );
    }

    private User requireAuthenticatedUser(
            String authenticatedEmail
    ) {
        if (authenticatedEmail == null
                || authenticatedEmail.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated user is required"
            );
        }

        return userRepository
                .findByEmail(
                        authenticatedEmail
                )
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.UNAUTHORIZED,
                                        "Authenticated user not found"
                                )
                );
    }

    private DeviceCredentialResponse toResponse(
            DeviceCredentialService.IssuedDeviceCredential issued
    ) {
        return new DeviceCredentialResponse(
                issued.credential(),
                issued.version(),
                issued.issuedAt()
        );
    }
}