package com.remote.core.service;

import com.remote.auth.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import static com.remote.common.ServerConstants.AUTH_BEARER_PREFIX;

@Service
public class CurrentUserService {

    private final JwtUtil jwtUtil;

    public CurrentUserService(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    public String extractUsername(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith(AUTH_BEARER_PREFIX)) {
            throw new IllegalArgumentException("Authorization header is missing");
        }

        String token = authHeader.substring(AUTH_BEARER_PREFIX.length());

        if (!jwtUtil.validateToken(token)) {
            throw new IllegalArgumentException("Invalid JWT token");
        }

        return jwtUtil.extractUsername(token);
    }
}