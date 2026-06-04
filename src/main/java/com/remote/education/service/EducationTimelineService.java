package com.remote.education.service;

import com.remote.core.exception.BusinessException;
import com.remote.education.dto.EducationTimelineEventResponse;
import com.remote.education.model.EducationParticipantStatus;
import com.remote.education.model.EducationSession;
import com.remote.education.model.EducationSessionEvent;
import com.remote.education.repository.EducationSessionEventRepository;
import com.remote.education.repository.EducationSessionParticipantRepository;
import com.remote.education.repository.EducationSessionRepository;
import com.remote.core.model.User;
import com.remote.core.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EducationTimelineService {

    private final EducationSessionRepository sessionRepository;
    private final EducationSessionEventRepository eventRepository;
    private final EducationSessionParticipantRepository participantRepository;
    private final UserRepository userRepository;

    public EducationTimelineService(EducationSessionRepository sessionRepository,
                                    EducationSessionEventRepository eventRepository,
                                    EducationSessionParticipantRepository participantRepository,
                                    UserRepository userRepository) {
        this.sessionRepository = sessionRepository;
        this.eventRepository = eventRepository;
        this.participantRepository = participantRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<EducationTimelineEventResponse> getTimeline(String username, String sessionCode) {
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> notFound("USER_NOT_FOUND", "Пользователь не найден"));

        EducationSession session = sessionRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> notFound("EDUCATION_SESSION_NOT_FOUND", "Учебная сессия не найдена"));

        checkAccess(session, currentUser);

        return eventRepository.findByEducationSessionOrderByCreatedAtDesc(session)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private void checkAccess(EducationSession session, User currentUser) {
        boolean isTeacher = session.getTeacher() != null
                && session.getTeacher().getId().equals(currentUser.getId());

        boolean isApprovedStudent = participantRepository
                .findByEducationSessionAndStudent(session, currentUser)
                .map(participant -> participant.getStatus() == EducationParticipantStatus.APPROVED)
                .orElse(false);

        if (!isTeacher && !isApprovedStudent) {
            throw forbidden("EDUCATION_TIMELINE_ACCESS_DENIED", "Нет доступа к истории этой учебной сессии");
        }
    }

    private EducationTimelineEventResponse toResponse(EducationSessionEvent event) {
        return new EducationTimelineEventResponse(
                event.getId(),
                event.getType().name(),
                event.getActor() != null ? event.getActor().getUsername() : null,
                event.getMessage(),
                event.getCreatedAt()
        );
    }

    private BusinessException notFound(String code, String message) {
        return new BusinessException(HttpStatus.NOT_FOUND, code, message);
    }

    private BusinessException forbidden(String code, String message) {
        return new BusinessException(HttpStatus.FORBIDDEN, code, message);
    }
}