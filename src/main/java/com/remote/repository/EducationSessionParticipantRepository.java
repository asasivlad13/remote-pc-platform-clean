package com.remote.repository;

import com.remote.model.EducationParticipantStatus;
import com.remote.model.EducationSession;
import com.remote.model.EducationSessionParticipant;
import com.remote.model.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EducationSessionParticipantRepository extends JpaRepository<EducationSessionParticipant, Long> {

    @EntityGraph(attributePaths = {
            "student",
            "educationSession",
            "educationSession.teacher",
            "educationSession.teacherPc"
    })
    List<EducationSessionParticipant> findByEducationSessionOrderByJoinedAtAsc(EducationSession educationSession);

    @EntityGraph(attributePaths = {
            "student",
            "educationSession",
            "educationSession.teacher",
            "educationSession.teacherPc"
    })
    List<EducationSessionParticipant> findByEducationSessionAndStatusOrderByJoinedAtAsc(
            EducationSession educationSession,
            EducationParticipantStatus status
    );

    @EntityGraph(attributePaths = {
            "student",
            "educationSession",
            "educationSession.teacher",
            "educationSession.teacherPc"
    })
    Optional<EducationSessionParticipant> findByEducationSessionAndStudent(
            EducationSession educationSession,
            User student
    );

    @EntityGraph(attributePaths = {
            "student",
            "educationSession",
            "educationSession.teacher",
            "educationSession.teacherPc"
    })
    Optional<EducationSessionParticipant> findWithDetailsById(Long id);

    long countByEducationSessionAndStatus(
            EducationSession educationSession,
            EducationParticipantStatus status
    );
    List<EducationSessionParticipant> findByEducationSessionAndHasControlTrue(EducationSession educationSession);

    Optional<EducationSessionParticipant> findFirstByStudentAndStatusInOrderByJoinedAtDesc(
            User student,
            Collection<EducationParticipantStatus> statuses
    );
}