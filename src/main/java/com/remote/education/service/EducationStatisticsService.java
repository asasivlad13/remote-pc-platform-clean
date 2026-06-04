package com.remote.education.service;

import com.remote.core.exception.BusinessException;
import com.remote.education.dto.EducationSessionStatisticsResponse;
import com.remote.education.model.*;
import com.remote.education.repository.EducationFileTransferRepository;
import com.remote.education.repository.EducationSessionEventRepository;
import com.remote.education.repository.EducationSessionParticipantRepository;
import com.remote.education.repository.EducationSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EducationStatisticsService {

    private final EducationSessionRepository sessionRepository;
    private final EducationSessionParticipantRepository participantRepository;
    private final EducationFileTransferRepository fileRepository;
    private final EducationSessionEventRepository eventRepository;

    public EducationStatisticsService(EducationSessionRepository sessionRepository,
                                      EducationSessionParticipantRepository participantRepository,
                                      EducationFileTransferRepository fileRepository,
                                      EducationSessionEventRepository eventRepository) {
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.fileRepository = fileRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional(readOnly = true)
    public EducationSessionStatisticsResponse getStatistics(String username, String sessionCode) {
        EducationSession session = sessionRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> notFound("EDUCATION_SESSION_NOT_FOUND", "Учебная сессия не найдена"));

        if (session.getTeacher() == null || !session.getTeacher().getUsername().equals(username)) {
            throw forbidden("ONLY_TEACHER_CAN_VIEW_STATISTICS", "Только преподаватель может видеть статистику сессии");
        }

        List<EducationSessionParticipant> participants =
                participantRepository.findByEducationSessionOrderByJoinedAtAsc(session);

        List<EducationFileTransfer> files =
                fileRepository.findByEducationSessionOrderByCreatedAtDesc(session);

        List<EducationSessionEvent> events =
                eventRepository.findByEducationSessionOrderByCreatedAtDesc(session);

        long approved = countByStatus(participants, EducationParticipantStatus.APPROVED);
        long waiting = countByStatus(participants, EducationParticipantStatus.WAITING);
        long rejected = countByStatus(participants, EducationParticipantStatus.REJECTED);
        long left = countByStatus(participants, EducationParticipantStatus.LEFT);

        long studentsWithControl = participants.stream()
                .filter(EducationSessionParticipant::isHasControl)
                .count();

        long activeScreenShares = participants.stream()
                .filter(EducationSessionParticipant::isScreenShareActive)
                .count();

        long requestedScreenShares = participants.stream()
                .filter(EducationSessionParticipant::isScreenShareRequested)
                .count();

        long totalFileSizeBytes = files.stream()
                .map(EducationFileTransfer::getSizeBytes)
                .filter(size -> size != null)
                .mapToLong(Long::longValue)
                .sum();

        return new EducationSessionStatisticsResponse(
                session.getSessionCode(),
                session.getTitle(),
                session.getStatus().name(),

                participants.size(),
                approved,
                waiting,
                rejected,
                left,

                studentsWithControl,
                activeScreenShares,
                requestedScreenShares,

                files.size(),
                totalFileSizeBytes,

                events.size()
        );
    }

    private long countByStatus(List<EducationSessionParticipant> participants,
                               EducationParticipantStatus status) {
        return participants.stream()
                .filter(participant -> participant.getStatus() == status)
                .count();
    }

    private BusinessException notFound(String code, String message) {
        return new BusinessException(HttpStatus.NOT_FOUND, code, message);
    }

    private BusinessException forbidden(String code, String message) {
        return new BusinessException(HttpStatus.FORBIDDEN, code, message);
    }
}