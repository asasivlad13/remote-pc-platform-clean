package com.remote.auth.repository;

import com.remote.auth.model.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {
    Optional<LoginAttempt> findByIpAddress(String ipAddress);
}