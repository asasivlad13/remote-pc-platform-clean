package com.remote.auth.service;

import com.remote.auth.repository.LoginAttemptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
public class LoginAttemptService {

    private static final int MAX_FAILED_ATTEMPTS =
            5;

    private static final Duration BLOCK_DURATION =
            Duration.ofMinutes(15);

    private static final int MAX_IP_ADDRESS_LENGTH =
            45;

    private final LoginAttemptRepository loginAttemptRepository;

    public LoginAttemptService(
            LoginAttemptRepository loginAttemptRepository
    ) {
        this.loginAttemptRepository =
                loginAttemptRepository;
    }

    @Transactional(readOnly = true)
    public boolean isBlocked(
            String ipAddress
    ) {
        String normalizedIpAddress =
                normalizeIpAddress(
                        ipAddress
                );

        return loginAttemptRepository
                .existsByIpAddressAndBlockUntilAfter(
                        normalizedIpAddress,
                        Instant.now()
                );
    }

    /*
     * Неудачная попытка сохраняется независимо
     * от основной login-транзакции.
     *
     * Даже если авторизация завершится исключением
     * и внешняя транзакция будет откатана,
     * security-счётчик останется записан.
     */
    @Transactional(
            propagation = Propagation.REQUIRES_NEW
    )
    public void registerFailedAttempt(
            String ipAddress
    ) {
        String normalizedIpAddress =
                normalizeIpAddress(
                        ipAddress
                );

        Instant now =
                Instant.now();

        loginAttemptRepository
                .recordFailedAttempt(
                        normalizedIpAddress,
                        now,
                        now.plus(
                                BLOCK_DURATION
                        ),
                        MAX_FAILED_ATTEMPTS
                );
    }

    /*
     * После успешной проверки credentials
     * старые failed attempts этого IP удаляются.
     *
     * Здесь используется обычная REQUIRED-транзакция,
     * поэтому операция входит в login transaction.
     */
    @Transactional
    public void clearFailures(
            String ipAddress
    ) {
        String normalizedIpAddress =
                normalizeIpAddress(
                        ipAddress
                );

        loginAttemptRepository
                .deleteByIpAddress(
                        normalizedIpAddress
                );
    }

    private String normalizeIpAddress(
            String value
    ) {
        if (value == null
                || value.isBlank()) {

            throw new IllegalArgumentException(
                    "Client IP address is required"
            );
        }

        String normalized =
                value.strip();

        if (normalized.length()
                > MAX_IP_ADDRESS_LENGTH) {

            throw new IllegalArgumentException(
                    "Client IP address is too long"
            );
        }

        return normalized;
    }
}