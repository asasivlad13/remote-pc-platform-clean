package com.remote.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {

    /*
     * Legacy lifetime.
     *
     * Пока используется старым /auth/login,
     * в том числе агентом.
     */
    @Value("${jwt.expiration.ms}")
    private Long expirationMs;

    /*
     * Короткий access token пользовательской auth-session.
     *
     * По умолчанию 15 минут.
     */
    @Value("${jwt.session.access.expiration.ms:900000}")
    private Long sessionAccessExpirationMs;

    @Value("${jwt.secret}")
    private String secret;

    private Key getSigningKey() {
        byte[] keyBytes =
                secret.getBytes();

        return Keys.hmacShaKeyFor(
                keyBytes
        );
    }

    /*
     * Legacy JWT.
     *
     * Метод сохраняется для совместимости
     * старого frontend и агента.
     */
    public String generateToken(
            String username
    ) {
        long now =
                System.currentTimeMillis();

        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(
                        new Date(now)
                )
                .setExpiration(
                        new Date(
                                now + expirationMs
                        )
                )
                .signWith(
                        getSigningKey(),
                        SignatureAlgorithm.HS256
                )
                .compact();
    }

    /*
     * Короткий access JWT пользовательской auth-session.
     *
     * sid не является секретом.
     * Он связывает JWT с серверной auth-session
     * и пригодится при дальнейшей проверке/revocation.
     */
    public String generateSessionAccessToken(
            String email,
            UUID sessionId
    ) {
        long now =
                System.currentTimeMillis();

        return Jwts.builder()
                .setSubject(email)
                .claim(
                        "sid",
                        sessionId.toString()
                )
                .claim(
                        "token_type",
                        "session_access"
                )
                .setIssuedAt(
                        new Date(now)
                )
                .setExpiration(
                        new Date(
                                now
                                        + sessionAccessExpirationMs
                        )
                )
                .signWith(
                        getSigningKey(),
                        SignatureAlgorithm.HS256
                )
                .compact();
    }

    public String extractUsername(
            String token
    ) {
        return getClaims(
                token
        ).getSubject();
    }

    public boolean validateToken(
            String token
    ) {
        try {
            getClaims(
                    token
            );

            return true;

        } catch (JwtException
                 | IllegalArgumentException e) {

            return false;
        }
    }

    private Claims getClaims(
            String token
    ) {
        return Jwts.parserBuilder()
                .setSigningKey(
                        getSigningKey()
                )
                .build()
                .parseClaimsJws(
                        token
                )
                .getBody();
    }
}