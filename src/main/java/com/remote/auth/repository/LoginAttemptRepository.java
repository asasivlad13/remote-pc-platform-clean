package com.remote.auth.repository;

import com.remote.auth.model.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface LoginAttemptRepository
        extends JpaRepository<LoginAttempt, Long> {

    boolean existsByIpAddressAndBlockUntilAfter(
            String ipAddress,
            Instant now
    );

    /*
     * PostgreSQL atomic UPSERT.
     *
     * В отличие от схемы:
     *
     * SELECT -> attempts++ -> UPDATE
     *
     * параллельные запросы не могут потерять
     * инкремент счётчика.
     *
     * Если другой запрос уже успел установить
     * активную блокировку, дополнительный запрос
     * не начинает новый цикл attempts.
     */
    @Modifying
    @Query(
            value = """
                    INSERT INTO login_attempts (
                        ip_address,
                        attempts,
                        block_until,
                        last_attempt
                    )
                    VALUES (
                        :ipAddress,
                        1,
                        NULL,
                        :now
                    )
                    ON CONFLICT (ip_address)
                    DO UPDATE SET
                        attempts =
                            CASE
                                WHEN login_attempts.block_until IS NOT NULL
                                     AND login_attempts.block_until > :now
                                    THEN login_attempts.attempts

                                WHEN login_attempts.attempts + 1 >= :maxFailedAttempts
                                    THEN 0

                                ELSE login_attempts.attempts + 1
                            END,

                        block_until =
                            CASE
                                WHEN login_attempts.block_until IS NOT NULL
                                     AND login_attempts.block_until > :now
                                    THEN login_attempts.block_until

                                WHEN login_attempts.attempts + 1 >= :maxFailedAttempts
                                    THEN :blockUntil

                                ELSE NULL
                            END,

                        last_attempt = :now
                    """,
            nativeQuery = true
    )
    int recordFailedAttempt(
            @Param("ipAddress")
            String ipAddress,

            @Param("now")
            Instant now,

            @Param("blockUntil")
            Instant blockUntil,

            @Param("maxFailedAttempts")
            int maxFailedAttempts
    );

    @Modifying
    @Query("""
            delete from LoginAttempt attempt
            where attempt.ipAddress = :ipAddress
            """)
    int deleteByIpAddress(
            @Param("ipAddress")
            String ipAddress
    );
}