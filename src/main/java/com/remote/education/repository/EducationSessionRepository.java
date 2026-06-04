package com.remote.education.repository;

import com.remote.education.model.EducationSession;
import com.remote.education.model.EducationSessionStatus;
import com.remote.core.model.User;
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

    Optional<EducationSession> findFirstByTeacherAndStatusOrderByCreatedAtDesc(
            User teacher,
            EducationSessionStatus status
    );
}