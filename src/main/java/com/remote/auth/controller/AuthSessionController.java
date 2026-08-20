package com.remote.auth.controller;

import com.remote.auth.dto.AuthMessageResponse;
import com.remote.auth.dto.AuthSessionResponse;
import com.remote.auth.dto.LoginRequest;
import com.remote.auth.service.AuthSessionService;
import com.remote.core.service.ClientIpService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;

@RestController
@RequestMapping("/auth/session")
public class AuthSessionController {

    private static final String REFRESH_COOKIE_PATH =
            "/auth/session";

    private final AuthSessionService authSessionService;
    private final ClientIpService clientIpService;

    private final boolean secureCookie;

    public AuthSessionController(
            AuthSessionService authSessionService,
            ClientIpService clientIpService,
            @Value("${auth.session.cookie.secure:false}")
            boolean secureCookie
    ) {
        this.authSessionService =
                authSessionService;

        this.clientIpService =
                clientIpService;

        this.secureCookie =
                secureCookie;
    }

    @PostMapping("/login")
    public AuthSessionResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        AuthSessionService.SessionTokens tokens =
                authSessionService.login(
                        request,
                        clientIpService.getClientIp(
                                httpRequest
                        ),
                        httpRequest.getHeader(
                                "User-Agent"
                        )
                );

        writeRefreshCookie(
                httpResponse,
                tokens
        );

        return toResponse(
                tokens
        );
    }

    @PostMapping("/refresh")
    public AuthSessionResponse refresh(
            @CookieValue(
                    name = AuthSessionService.REFRESH_COOKIE_NAME,
                    required = false
            )
            String refreshToken,
            HttpServletResponse httpResponse
    ) {
        AuthSessionService.SessionTokens tokens =
                authSessionService.refresh(
                        refreshToken
                );

        writeRefreshCookie(
                httpResponse,
                tokens
        );

        return toResponse(
                tokens
        );
    }

    @PostMapping("/logout")
    public AuthMessageResponse logout(
            @CookieValue(
                    name = AuthSessionService.REFRESH_COOKIE_NAME,
                    required = false
            )
            String refreshToken,
            HttpServletResponse httpResponse
    ) {
        authSessionService.logout(
                refreshToken
        );

        clearRefreshCookie(
                httpResponse
        );

        return new AuthMessageResponse(
                "Logged out successfully"
        );
    }

    private AuthSessionResponse toResponse(
            AuthSessionService.SessionTokens tokens
    ) {
        return new AuthSessionResponse(
                tokens.accessToken(),
                tokens.sessionId(),
                tokens.refreshExpiresAt()
        );
    }

    private void writeRefreshCookie(
            HttpServletResponse response,
            AuthSessionService.SessionTokens tokens
    ) {
        Duration remainingLifetime =
                Duration.between(
                        Instant.now(),
                        tokens.refreshExpiresAt()
                );

        if (remainingLifetime.isNegative()) {
            remainingLifetime =
                    Duration.ZERO;
        }

        ResponseCookie cookie =
                ResponseCookie
                        .from(
                                AuthSessionService.REFRESH_COOKIE_NAME,
                                tokens.refreshToken()
                        )
                        .httpOnly(true)
                        .secure(
                                secureCookie
                        )
                        .sameSite(
                                "Strict"
                        )
                        .path(
                                REFRESH_COOKIE_PATH
                        )
                        .maxAge(
                                remainingLifetime
                        )
                        .build();

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookie.toString()
        );
    }

    private void clearRefreshCookie(
            HttpServletResponse response
    ) {
        ResponseCookie cookie =
                ResponseCookie
                        .from(
                                AuthSessionService.REFRESH_COOKIE_NAME,
                                ""
                        )
                        .httpOnly(true)
                        .secure(
                                secureCookie
                        )
                        .sameSite(
                                "Strict"
                        )
                        .path(
                                REFRESH_COOKIE_PATH
                        )
                        .maxAge(
                                Duration.ZERO
                        )
                        .build();

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookie.toString()
        );
    }
}