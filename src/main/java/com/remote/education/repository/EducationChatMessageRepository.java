package com.remote.education.repository;

import com.remote.education.model.EducationChatMessage;
import com.remote.education.model.EducationSession;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EducationChatMessageRepository extends JpaRepository<EducationChatMessage, Long> {

    @EntityGraph(attributePaths = {"sender", "recipient", "educationSession"})
    List<EducationChatMessage> findByEducationSessionOrderByCreatedAtAsc(EducationSession educationSession);
}