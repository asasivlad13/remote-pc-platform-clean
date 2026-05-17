package com.remote.repository;

import com.remote.model.EducationSession;
import com.remote.model.EducationSessionStatus;
import com.remote.model.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EducationSessionRepository extends JpaRepository<EducationSession, Long> {

    @EntityGraph(attributePaths = {"teacher", "teacherPc"})
    Optional<EducationSession> findBySessionCode(String sessionCode);

    boolean existsBySessionCode(String sessionCode);

    @EntityGraph(attributePaths = {"teacher", "teacherPc"})
    List<EducationSession> findByTeacherOrderByCreatedAtDesc(User teacher);

    @EntityGraph(attributePaths = {"teacher", "teacherPc"})
    List<EducationSession> findByTeacherAndStatusOrderByCreatedAtDesc(
            User teacher,
            EducationSessionStatus status
    );

    Optional<EducationSession> findFirstByTeacherAndStatusOrderByCreatedAtDesc(
            User teacher,
            EducationSessionStatus status
    );
}