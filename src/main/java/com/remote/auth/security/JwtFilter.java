package com.remote.auth.security;

import com.remote.core.model.User;
import com.remote.core.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import static com.remote.common.ServerConstants.AUTH_BEARER_PREFIX;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    public JwtFilter(
            JwtUtil jwtUtil,
            UserRepository userRepository
    ) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {

        String authHeader =
                request.getHeader("Authorization");

        if (authHeader != null
                && authHeader.startsWith(
                AUTH_BEARER_PREFIX
        )) {

            String token =
                    authHeader.substring(
                            AUTH_BEARER_PREFIX.length()
                    );

            if (jwtUtil.validateToken(token)) {

                /*
                 * JWT subject теперь содержит email.
                 *
                 * Метод JwtUtil пока называется extractUsername()
                 * только ради совместимости с остальным backend.
                 */
                String email =
                        jwtUtil.extractUsername(token);

                User user =
                        userRepository.findByEmail(email)
                                .orElse(null);

                if (user != null
                        && user.isEnabled()
                        && user.isAccountNonLocked()
                        && user.isAccountNonExpired()
                        && user.isCredentialsNonExpired()) {

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    user,
                                    null,
                                    user.getAuthorities()
                            );

                    SecurityContextHolder
                            .getContext()
                            .setAuthentication(authentication);
                }
            }
        }

        chain.doFilter(
                request,
                response
        );
    }
}