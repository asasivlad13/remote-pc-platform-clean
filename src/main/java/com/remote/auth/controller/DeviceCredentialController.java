package com.remote.auth.controller;

import com.remote.auth.dto.AuthMessageResponse;
import com.remote.auth.dto.DeviceCredentialResponse;
import com.remote.auth.service.DeviceCredentialManagementService;
import com.remote.core.service.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/auth/device-credentials")
public class DeviceCredentialController {

    private final DeviceCredentialManagementService managementService;
    private final CurrentUserService currentUserService;

    /*
     * Первоначальная выдача credential для уже
     * привязанного к пользователю ПК.
     */
    @PostMapping("/{pcId}")
    public DeviceCredentialResponse issueCredential(
            @PathVariable Long pcId,
            HttpServletRequest request
    ) {
        String email =
                currentUserService
                        .extractUsername(
                                request
                        );

        return managementService
                .issueCredential(
                        pcId,
                        email
                );
    }

    /*
     * Полностью заменяет текущий secret.
     *
     * Старый credential после успешной rotation
     * больше не проходит device-auth.
     */
    @PostMapping("/{pcId}/rotate")
    public DeviceCredentialResponse rotateCredential(
            @PathVariable Long pcId,
            HttpServletRequest request
    ) {
        String email =
                currentUserService
                        .extractUsername(
                                request
                        );

        return managementService
                .rotateCredential(
                        pcId,
                        email
                );
    }

    /*
     * Пользователь вручную отзывает credential.
     */
    @DeleteMapping("/{pcId}")
    public AuthMessageResponse revokeCredential(
            @PathVariable Long pcId,
            HttpServletRequest request
    ) {
        String email =
                currentUserService
                        .extractUsername(
                                request
                        );

        managementService
                .revokeCredential(
                        pcId,
                        email
                );

        return new AuthMessageResponse(
                "Device credential revoked successfully"
        );
    }
}