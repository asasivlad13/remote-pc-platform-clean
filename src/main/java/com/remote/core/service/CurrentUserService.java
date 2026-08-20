package com.remote.core.service;

import com.remote.auth.service.AuthSessionSecurityService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import static com.remote.common.ServerConstants.AUTH_BEARER_PREFIX;

@Service
public class CurrentUserService {

    private final AuthSessionSecurityService authSessionSecurityService;

    public CurrentUserService(
            AuthSessionSecurityService authSessionSecurityService
    ) {
        this.authSessionSecurityService =
                authSessionSecurityService;
    }

    public String extractUsername(
            HttpServletRequest request
    ) {
        String authHeader =
                request.getHeader(
                        "Authorization"
                );

        if (authHeader == null
                || !authHeader.startsWith(
                AUTH_BEARER_PREFIX
        )) {
            throw new IllegalArgumentException(
                    "Authorization header is missing"
            );
        }

        String token =
                authHeader.substring(
                        AUTH_BEARER_PREFIX.length()
                );

        return authSessionSecurityService
                .validateAndExtractEmail(
                        token
                )
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Invalid JWT token"
                                )
                );
    }
}