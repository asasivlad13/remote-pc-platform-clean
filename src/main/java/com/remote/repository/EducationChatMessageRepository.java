package com.remote.repository;

import com.remote.model.EducationChatMessage;
import com.remote.model.EducationSession;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EducationChatMessageRepository extends JpaRepository<EducationChatMessage, Long> {

    @EntityGraph(attributePaths = {"sender", "recipient", "educationSession"})
    List<EducationChatMessage> findByEducationSessionOrderByCreatedAtAsc(EducationSession educationSession);
}