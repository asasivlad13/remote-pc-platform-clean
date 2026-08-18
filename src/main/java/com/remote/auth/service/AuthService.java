package com.remote.auth.service;

import com.remote.auth.dto.AuthMessageResponse;
import com.remote.auth.dto.AuthTokenResponse;
import com.remote.auth.dto.ChangePasswordRequest;
import com.remote.auth.dto.LoginRequest;
import com.remote.auth.dto.RegisterRequest;
import com.remote.auth.dto.RegisterResponse;
import com.remote.auth.dto.VerifyEmailRequest;
import com.remote.auth.model.LoginAttempt;
import com.remote.auth.repository.LoginAttemptRepository;
import com.remote.auth.security.JwtUtil;
import com.remote.core.model.AccountStatus;
import com.remote.core.model.User;
import com.remote.core.repository.UserRepository;
import com.remote.history.model.ConnectionLog;
import com.remote.history.repository.ConnectionLogRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static com.remote.common.ServerConstants.AUTH_BEARER_PREFIX;

@Service
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int BLOCK_MINUTES = 15;

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final LoginAttemptRepository loginAttemptRepository;
    private final ConnectionLogRepository connectionLogRepository;
    private final EmailVerificationService emailVerificationService;
    private final PasswordPolicyService passwordPolicyService;

    private final BCryptPasswordEncoder encoder =
            new BCryptPasswordEncoder();

    public AuthService(
            UserRepository userRepository,
            JwtUtil jwtUtil,
            LoginAttemptRepository loginAttemptRepository,
            ConnectionLogRepository connectionLogRepository,
            EmailVerificationService emailVerificationService,
            PasswordPolicyService passwordPolicyService
    ) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.loginAttemptRepository =
                loginAttemptRepository;
        this.connectionLogRepository =
                connectionLogRepository;
        this.emailVerificationService =
                emailVerificationService;
        this.passwordPolicyService =
                passwordPolicyService;
    }

    @Transactional
    public RegisterResponse register(
            RegisterRequest request,
            String ipAddress
    ) {
        checkIpNotBlocked(
                ipAddress,
                "Too many attempts. Try again later."
        );

        String email =
                normalizeEmail(
                        request.email()
                );

        String displayName =
                normalizeDisplayName(
                        request.displayName()
                );

        passwordPolicyService
                .validateRegistrationPassword(
                        email,
                        request.password(),
                        request.confirmPassword()
                );

        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Email already exists"
            );
        }

        User user =
                new User();

        /*
         * Временный compatibility-мост.
         *
         * Пока остальной backend использует username,
         * туда записывается тот же нормализованный email.
         */
        user.setUsername(email);

        user.setEmail(email);
        user.setDisplayName(displayName);

        user.setPassword(
                encoder.encode(
                        request.password()
                )
        );

        user.setStatus(
                AccountStatus.EMAIL_NOT_VERIFIED
        );

        user.setPasswordChangedAt(
                Instant.now()
        );

        User savedUser =
                userRepository.save(user);

        /*
         * Создание пользователя и verification token
         * выполняются в одной транзакции.
         */
        String verificationToken =
                emailVerificationService
                        .createToken(
                                savedUser
                        );

        return new RegisterResponse(
                "User registered successfully. Email verification required.",
                verificationToken
        );
    }

    @Transactional
    public AuthMessageResponse verifyEmail(
            VerifyEmailRequest request
    ) {
        emailVerificationService.verify(
                request.token()
        );

        return new AuthMessageResponse(
                "Email verified successfully"
        );
    }

    @Transactional
    public AuthTokenResponse login(
            LoginRequest request,
            String ipAddress
    ) {
        checkIpNotBlocked(
                ipAddress,
                "Too many failed attempts. Try again in 15 minutes."
        );

        String email =
                normalizeEmail(
                        request.identifier()
                );

        User user =
                userRepository
                        .findByEmail(email)
                        .orElse(null);

        if (user == null
                || !encoder.matches(
                request.password(),
                user.getPassword()
        )) {
            registerFailedAttempt(
                    ipAddress
            );

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid credentials"
            );
        }

        checkAccountCanLogin(user);

        loginAttemptRepository
                .findByIpAddress(ipAddress)
                .ifPresent(
                        loginAttemptRepository::delete
                );

        String token =
                jwtUtil.generateToken(
                        user.getEmail()
                );

        return new AuthTokenResponse(
                token
        );
    }

    @Transactional
    public AuthMessageResponse changePassword(
            String authHeader,
            ChangePasswordRequest request
    ) {
        String email =
                extractEmailFromAuthHeader(
                        authHeader
                );

        User user =
                userRepository
                        .findByEmail(email)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "User not found"
                                        )
                        );

        if (!encoder.matches(
                request.oldPassword(),
                user.getPassword()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Old password is incorrect"
            );
        }

        passwordPolicyService
                .validatePasswordStrength(
                        email,
                        request.newPassword()
                );

        user.setPassword(
                encoder.encode(
                        request.newPassword()
                )
        );

        user.setPasswordChangedAt(
                Instant.now()
        );

        userRepository.save(user);

        return new AuthMessageResponse(
                "Password changed successfully"
        );
    }

    @Transactional(readOnly = true)
    public List<ConnectionLog> getLogs(
            String authHeader
    ) {
        String email =
                extractEmailFromAuthHeader(
                        authHeader
                );

        /*
         * ConnectionLog пока является legacy-моделью.
         * В колонке username временно хранится email.
         */
        return connectionLogRepository
                .findByUsernameOrderByTimestampDesc(
                        email
                );
    }

    private void checkAccountCanLogin(
            User user
    ) {
        if (user.getStatus()
                == AccountStatus.EMAIL_NOT_VERIFIED) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Email is not verified"
            );
        }

        if (user.getStatus()
                == AccountStatus.BLOCKED) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Account is blocked"
            );
        }

        if (user.getStatus()
                == AccountStatus.DISABLED) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Account is disabled"
            );
        }

        if (user.getStatus()
                != AccountStatus.ACTIVE) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Account is not active"
            );
        }
    }

    private String normalizeEmail(
            String value
    ) {
        if (value == null
                || value.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Email is required"
            );
        }

        String email =
                value.strip()
                        .toLowerCase(
                                Locale.ROOT
                        );

        if (email.length() > 254
                || email.contains(" ")
                || !email.matches(
                "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"
        )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid email"
            );
        }

        return email;
    }

    private String normalizeDisplayName(
            String value
    ) {
        if (value == null
                || value.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Display name is required"
            );
        }

        String displayName =
                value.strip();

        if (displayName.length() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Display name is too long"
            );
        }

        return displayName;
    }

    private void checkIpNotBlocked(
            String ipAddress,
            String message
    ) {
        if (isBlocked(ipAddress)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    message
            );
        }
    }

    private boolean isBlocked(
            String ipAddress
    ) {
        return loginAttemptRepository
                .findByIpAddress(ipAddress)
                .filter(
                        attempt ->
                                attempt.getBlockUntil()
                                        != null
                )
                .filter(
                        attempt ->
                                LocalDateTime.now()
                                        .isBefore(
                                                attempt.getBlockUntil()
                                        )
                )
                .isPresent();
    }

    private void registerFailedAttempt(
            String ipAddress
    ) {
        LoginAttempt attempt =
                loginAttemptRepository
                        .findByIpAddress(
                                ipAddress
                        )
                        .orElse(
                                new LoginAttempt(
                                        ipAddress
                                )
                        );

        attempt.setAttempts(
                attempt.getAttempts() + 1
        );

        attempt.setLastAttempt(
                LocalDateTime.now()
        );

        if (attempt.getAttempts()
                >= MAX_FAILED_ATTEMPTS) {

            attempt.setBlockUntil(
                    LocalDateTime.now()
                            .plusMinutes(
                                    BLOCK_MINUTES
                            )
            );

            attempt.setAttempts(0);
        }

        loginAttemptRepository.save(
                attempt
        );
    }

    private String extractEmailFromAuthHeader(
            String authHeader
    ) {
        if (authHeader == null
                || !authHeader.startsWith(
                AUTH_BEARER_PREFIX
        )) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authorization header is missing"
            );
        }

        String token =
                authHeader.substring(
                        AUTH_BEARER_PREFIX.length()
                );

        if (!jwtUtil.validateToken(token)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid token"
            );
        }

        return normalizeEmail(
                jwtUtil.extractUsername(token)
        );
    }
}