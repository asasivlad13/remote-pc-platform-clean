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

    public static final String SESSION_ACCESS_TOKEN_TYPE =
            "session_access";

    private static final String SESSION_ID_CLAIM =
            "sid";

    private static final String TOKEN_TYPE_CLAIM =
            "token_type";

    /*
     * Legacy lifetime.
     */
    @Value("${jwt.expiration.ms}")
    private Long expirationMs;

    /*
     * Новый короткий session access JWT.
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
     * Legacy JWT для старого frontend и агента.
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

    public String generateSessionAccessToken(
            String email,
            UUID sessionId
    ) {
        long now =
                System.currentTimeMillis();

        return Jwts.builder()
                .setSubject(email)
                .claim(
                        SESSION_ID_CLAIM,
                        sessionId.toString()
                )
                .claim(
                        TOKEN_TYPE_CLAIM,
                        SESSION_ACCESS_TOKEN_TYPE
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

    public String extractTokenType(
            String token
    ) {
        return getClaims(
                token
        ).get(
                TOKEN_TYPE_CLAIM,
                String.class
        );
    }

    public UUID extractSessionId(
            String token
    ) {
        String sessionId =
                getClaims(
                        token
                ).get(
                        SESSION_ID_CLAIM,
                        String.class
                );

        if (sessionId == null
                || sessionId.isBlank()) {

            throw new IllegalArgumentException(
                    "JWT session id is missing"
            );
        }

        return UUID.fromString(
                sessionId
        );
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