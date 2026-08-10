package com.remote.auth.service;

import com.remote.auth.dto.AuthMessageResponse;
import com.remote.auth.dto.AuthRequest;
import com.remote.auth.dto.AuthTokenResponse;
import com.remote.auth.dto.ChangePasswordRequest;
import com.remote.auth.model.LoginAttempt;
import com.remote.auth.repository.LoginAttemptRepository;
import com.remote.auth.security.JwtUtil;
import com.remote.core.model.User;
import com.remote.core.repository.UserRepository;
import com.remote.history.model.ConnectionLog;
import com.remote.history.repository.ConnectionLogRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static com.remote.common.ServerConstants.AUTH_BEARER_PREFIX;

@Service
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int BLOCK_MINUTES = 15;

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final LoginAttemptRepository loginAttemptRepository;
    private final ConnectionLogRepository connectionLogRepository;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthService(UserRepository userRepository,
                       JwtUtil jwtUtil,
                       LoginAttemptRepository loginAttemptRepository,
                       ConnectionLogRepository connectionLogRepository) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.loginAttemptRepository = loginAttemptRepository;
        this.connectionLogRepository = connectionLogRepository;
    }

    public AuthMessageResponse register(AuthRequest request, String ipAddress) {
        checkIpNotBlocked(ipAddress, "Too many attempts. Try again later.");

        if (userRepository.findByUsername(request.username()).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Username already exists"
            );
        }

        User user = new User();
        user.setUsername(request.username());
        user.setPassword(encoder.encode(request.password()));

        userRepository.save(user);

        return new AuthMessageResponse("User registered successfully");
    }

    public AuthTokenResponse login(AuthRequest request, String ipAddress) {
        checkIpNotBlocked(ipAddress, "Too many failed attempts. Try again in 15 minutes.");

        User user = userRepository.findByUsername(request.username())
                .orElse(null);

        if (user == null || !encoder.matches(request.password(), user.getPassword())) {
            registerFailedAttempt(ipAddress);

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid credentials"
            );
        }

        loginAttemptRepository.findByIpAddress(ipAddress)
                .ifPresent(loginAttemptRepository::delete);

        String token = jwtUtil.generateToken(user.getUsername());

        return new AuthTokenResponse(token);
    }

    public AuthMessageResponse changePassword(String authHeader, ChangePasswordRequest request) {
        String username = extractUsernameFromAuthHeader(authHeader);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found"
                ));

        if (!encoder.matches(request.oldPassword(), user.getPassword())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Old password is incorrect"
            );
        }

        user.setPassword(encoder.encode(request.newPassword()));
        userRepository.save(user);

        return new AuthMessageResponse("Password changed successfully");
    }

    public List<ConnectionLog> getLogs(String authHeader) {
        String username = extractUsernameFromAuthHeader(authHeader);
        return connectionLogRepository.findByUsernameOrderByTimestampDesc(username);
    }

    private void checkIpNotBlocked(String ipAddress, String message) {
        if (isBlocked(ipAddress)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
        }
    }

    private boolean isBlocked(String ipAddress) {
        return loginAttemptRepository.findByIpAddress(ipAddress)
                .filter(attempt -> attempt.getBlockUntil() != null)
                .filter(attempt -> LocalDateTime.now().isBefore(attempt.getBlockUntil()))
                .isPresent();
    }

    private void registerFailedAttempt(String ipAddress) {
        LoginAttempt attempt = loginAttemptRepository.findByIpAddress(ipAddress)
                .orElse(new LoginAttempt(ipAddress));

        attempt.setAttempts(attempt.getAttempts() + 1);
        attempt.setLastAttempt(LocalDateTime.now());

        if (attempt.getAttempts() >= MAX_FAILED_ATTEMPTS) {
            attempt.setBlockUntil(LocalDateTime.now().plusMinutes(BLOCK_MINUTES));
            attempt.setAttempts(0);
        }

        loginAttemptRepository.save(attempt);
    }

    private String extractUsernameFromAuthHeader(String authHeader) {
        if (authHeader == null || !authHeader.startsWith(AUTH_BEARER_PREFIX)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authorization header is missing"
            );
        }

        String token = authHeader.substring(AUTH_BEARER_PREFIX.length());

        if (!jwtUtil.validateToken(token)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid token"
            );
        }

        return jwtUtil.extractUsername(token);
    }
}