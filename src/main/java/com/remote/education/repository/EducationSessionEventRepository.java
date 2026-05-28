package com.remote.education.repository;

import com.remote.education.model.EducationSession;
import com.remote.education.model.EducationSessionEvent;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EducationSessionEventRepository extends JpaRepository<EducationSessionEvent, Long> {

    @EntityGraph(attributePaths = {"actor", "educationSession"})
    List<EducationSessionEvent> findByEducationSessionOrderByCreatedAtDesc(EducationSession educationSession);
}