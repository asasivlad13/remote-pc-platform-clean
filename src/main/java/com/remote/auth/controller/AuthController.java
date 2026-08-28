package com.remote.auth.controller;

import com.remote.auth.dto.AuthMessageResponse;
import com.remote.auth.dto.AuthTokenResponse;
import com.remote.auth.dto.ChangePasswordRequest;
import com.remote.auth.dto.ForgotPasswordRequest;
import com.remote.auth.dto.ForgotPasswordResponse;
import com.remote.auth.dto.LoginRequest;
import com.remote.auth.dto.RegisterRequest;
import com.remote.auth.dto.RegisterResponse;
import com.remote.auth.dto.ResetPasswordRequest;
import com.remote.auth.dto.VerifyEmailRequest;
import com.remote.auth.service.AuthService;
import com.remote.auth.service.PasswordResetService;
import com.remote.core.service.ClientIpService;
import com.remote.core.service.CurrentUserService;
import com.remote.history.dto.HistoryResponse;
import com.remote.history.service.HistoryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final ClientIpService clientIpService;
    private final CurrentUserService currentUserService;
    private final HistoryService historyService;

    @PostMapping("/register")
    public RegisterResponse register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest
    ) {
        return authService.register(
                request,
                clientIpService.getClientIp(
                        httpRequest
                )
        );
    }

    @PostMapping("/verify-email")
    public AuthMessageResponse verifyEmail(
            @Valid @RequestBody VerifyEmailRequest request
    ) {
        return authService.verifyEmail(
                request
        );
    }

    @PostMapping("/login")
    public AuthTokenResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        return authService.login(
                request,
                clientIpService.getClientIp(
                        httpRequest
                )
        );
    }

    @PostMapping("/forgot-password")
    public ForgotPasswordResponse forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request
    ) {
        return passwordResetService
                .requestReset(
                        request
                );
    }

    @PostMapping("/reset-password")
    public AuthMessageResponse resetPassword(
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        return passwordResetService
                .resetPassword(
                        request
                );
    }

    @PostMapping("/change-password")
    public AuthMessageResponse changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @RequestHeader("Authorization") String authHeader
    ) {
        return authService.changePassword(
                authHeader,
                request
        );
    }

    /*
     * Compatibility endpoint.
     *
     * Источником истории уже является RemoteSession,
     * как и для /api/history.
     */
    @GetMapping("/logs")
    public List<HistoryResponse> getLogs(
            HttpServletRequest request
    ) {
        String email =
                currentUserService
                        .extractUsername(
                                request
                        );

        return historyService
                .getHistory(
                        email
                );
    }
}