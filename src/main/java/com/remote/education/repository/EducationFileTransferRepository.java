package com.remote.education.repository;

import com.remote.education.model.EducationFileTransfer;
import com.remote.education.model.EducationSession;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EducationFileTransferRepository extends JpaRepository<EducationFileTransfer, Long> {

    @EntityGraph(attributePaths = {"educationSession", "sender", "recipient"})
    List<EducationFileTransfer> findByEducationSessionOrderByCreatedAtDesc(EducationSession educationSession);
}