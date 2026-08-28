package com.remote.education.service;

import com.remote.core.model.User;
import com.remote.core.repository.UserRepository;
import com.remote.education.model.EducationParticipantStatus;
import com.remote.education.model.EducationSession;
import com.remote.education.model.EducationSessionEventType;
import com.remote.education.model.EducationSessionParticipant;
import com.remote.education.repository.EducationSessionParticipantRepository;
import com.remote.education.repository.EducationSessionRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.remote.education.dto.EducationParticipantResponse;

import java.time.Instant;

@Service
public class EducationControlService {

    private final EducationSessionRepository sessionRepository;
    private final EducationSessionParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final EducationSessionEventService eventService;

    public EducationControlService(EducationSessionRepository sessionRepository,
                                   EducationSessionParticipantRepository participantRepository,
                                   UserRepository userRepository,
                                   EducationSessionEventService eventService) {
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.userRepository = userRepository;
        this.eventService = eventService;
    }

    @Transactional
    public EducationSessionParticipant requestControl(String username, String sessionCode) {
        User student = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        EducationSession session = sessionRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> new IllegalArgumentException("Учебная сессия не найдена"));

        EducationSessionParticipant participant = participantRepository
                .findByEducationSessionAndStudent(session, student)
                .orElseThrow(() -> new IllegalArgumentException("Вы не являетесь участником этой сессии"));

        if (participant.getStatus() != EducationParticipantStatus.APPROVED) {
            throw new IllegalArgumentException("Управление может запросить только подтверждённый ученик");
        }

        participant.setControlRequested(true);
        participant.setControlRequestedAt(Instant.now());
        participant.setLastActivityAt(Instant.now());

        EducationSessionParticipant saved = participantRepository.save(participant);

        eventService.log(
                session,
                student,
                EducationSessionEventType.CONTROL_REQUESTED,
                "Ученик " + saved.getDisplayName() + " запросил управление ПК преподавателя"
        );

        return saved;
    }

    @Transactional
    public EducationSessionParticipant grantControl(String teacherUsername, Long participantId) {
        EducationSessionParticipant participant = participantRepository.findWithDetailsById(participantId)
                .orElseThrow(() -> new IllegalArgumentException("Участник не найден"));

        EducationSession session = participant.getEducationSession();

        if (!session.getTeacher().getUsername().equals(teacherUsername)) {
            throw new IllegalArgumentException("Только преподаватель может выдавать управление");
        }

        if (!session.getAllowStudentControl()) {
            throw new IllegalArgumentException("В этой сессии управление ученикам запрещено");
        }

        if (participant.getStatus() != EducationParticipantStatus.APPROVED) {
            throw new IllegalArgumentException("Управление можно выдать только подтверждённому ученику");
        }

        for (EducationSessionParticipant active : participantRepository.findByEducationSessionAndHasControlTrue(session)) {
            active.setHasControl(false);
            active.setControlRequested(false);
            active.setLastActivityAt(Instant.now());
            participantRepository.save(active);
        }

        participant.setHasControl(true);
        participant.setControlRequested(false);
        participant.setControlGrantedAt(Instant.now());
        participant.setLastActivityAt(Instant.now());

        EducationSessionParticipant saved = participantRepository.save(participant);

        eventService.log(
                session,
                session.getTeacher(),
                EducationSessionEventType.CONTROL_GRANTED,
                "Преподаватель разрешил управление ученику " + saved.getDisplayName()
        );

        return saved;
    }

    @Transactional
    public EducationSessionParticipant rejectControl(String teacherUsername, Long participantId) {
        EducationSessionParticipant participant = participantRepository.findWithDetailsById(participantId)
                .orElseThrow(() -> new IllegalArgumentException("Участник не найден"));

        EducationSession session = participant.getEducationSession();

        if (!session.getTeacher().getUsername().equals(teacherUsername)) {
            throw new IllegalArgumentException("Только преподаватель может отклонить запрос управления");
        }

        participant.setControlRequested(false);
        participant.setHasControl(false);
        participant.setLastActivityAt(Instant.now());

        EducationSessionParticipant saved = participantRepository.save(participant);

        eventService.log(
                session,
                session.getTeacher(),
                EducationSessionEventType.CONTROL_REJECTED,
                "Преподаватель отклонил запрос управления ученика " + saved.getDisplayName()
        );

        return saved;
    }

    @Transactional
    public EducationSessionParticipant revokeControl(String teacherUsername, Long participantId) {
        EducationSessionParticipant participant = participantRepository.findWithDetailsById(participantId)
                .orElseThrow(() -> new IllegalArgumentException("Участник не найден"));

        EducationSession session = participant.getEducationSession();

        if (!session.getTeacher().getUsername().equals(teacherUsername)) {
            throw new IllegalArgumentException("Только преподаватель может забрать управление");
        }

        participant.setHasControl(false);
        participant.setControlRequested(false);
        participant.setLastActivityAt(Instant.now());

        EducationSessionParticipant saved = participantRepository.save(participant);

        eventService.log(
                session,
                session.getTeacher(),
                EducationSessionEventType.CONTROL_REVOKED,
                "Преподаватель забрал управление у ученика " + saved.getDisplayName()
        );

        return saved;
    }

    @Transactional(readOnly = true)
    public boolean hasControlInSession(String sessionCode) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return hasControlInSession(username, sessionCode);
    }

    @Transactional(readOnly = true)
    public boolean hasControlInSession(String username, String sessionCode) {
        User student = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        EducationSession session = sessionRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> new IllegalArgumentException("Учебная сессия не найдена"));

        return participantRepository.findByEducationSessionAndStudent(session, student)
                .map(participant ->
                        participant.getStatus() == EducationParticipantStatus.APPROVED
                                && participant.isHasControl()
                )
                .orElse(false);
    }

    @Transactional
    public EducationParticipantResponse requestControlResponse(String username, String sessionCode) {
        return toResponse(requestControl(username, sessionCode));
    }

    @Transactional
    public EducationParticipantResponse grantControlResponse(String username, Long participantId) {
        return toResponse(grantControl(username, participantId));
    }

    @Transactional
    public EducationParticipantResponse rejectControlResponse(String username, Long participantId) {
        return toResponse(rejectControl(username, participantId));
    }

    @Transactional
    public EducationParticipantResponse revokeControlResponse(String username, Long participantId) {
        return toResponse(revokeControl(username, participantId));
    }

    private EducationParticipantResponse toResponse(EducationSessionParticipant participant) {
        return new EducationParticipantResponse(
                participant.getId(),
                participant.getEducationSession().getSessionCode(),
                participant.getDisplayName(),
                participant.getStudent() != null ? participant.getStudent().getId() : null,
                participant.getStudent() != null ? participant.getStudent().getUsername() : null,
                participant.getStatus().name(),
                participant.getJoinedAt(),
                participant.getApprovedAt(),
                participant.isControlRequested(),
                participant.isHasControl(),
                participant.getControlRequestedAt(),
                participant.getControlGrantedAt(),
                participant.getLastActivityAt(),
                participant.isScreenShareRequested(),
                participant.isScreenShareActive(),
                participant.getScreenShareRequestedAt(),
                participant.getScreenShareStartedAt()
        );
    }
}