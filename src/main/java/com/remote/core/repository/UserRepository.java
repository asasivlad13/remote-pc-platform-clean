package com.remote.core.repository;

import com.remote.core.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /*
     * Временный compatibility-метод.
     *
     * Старый backend всё ещё вызывает findByUsername(...),
     * но фактически поиск уже выполняется по email.
     */
    @Query("""
            SELECT u
            FROM User u
            WHERE u.email = :email
            """)
    Optional<User> findByUsername(
            @Param("email") String email
    );

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}