package com.remote.core.service;

import com.remote.auth.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    private final JwtUtil jwtUtil;

    public CurrentUserService(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    public String extractUsername(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Authorization header is missing");
        }

        String token = authHeader.substring(7);

        if (!jwtUtil.validateToken(token)) {
            throw new IllegalArgumentException("Invalid JWT token");
        }

        return jwtUtil.extractUsername(token);
    }
}