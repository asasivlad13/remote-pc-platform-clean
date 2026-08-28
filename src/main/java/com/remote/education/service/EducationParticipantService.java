package com.remote.education.service;

import com.remote.core.exception.BusinessException;
import com.remote.core.model.User;
import com.remote.core.repository.UserRepository;
import com.remote.education.model.EducationParticipantStatus;
import com.remote.education.model.EducationSession;
import com.remote.education.model.EducationSessionEventType;
import com.remote.education.model.EducationSessionParticipant;
import com.remote.education.model.EducationSessionStatus;
import com.remote.education.repository.EducationSessionParticipantRepository;
import com.remote.education.repository.EducationSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.remote.education.dto.EducationParticipantResponse;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EducationParticipantService {

    private final EducationSessionRepository sessionRepository;
    private final EducationSessionParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final EducationSessionEventService eventService;

    public EducationParticipantService(EducationSessionRepository sessionRepository,
                                       EducationSessionParticipantRepository participantRepository,
                                       UserRepository userRepository,
                                       EducationSessionEventService eventService) {
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.userRepository = userRepository;
        this.eventService = eventService;
    }

    @Transactional
    public EducationSessionParticipant getMyParticipantStatus(String username, Long sessionId) {
        User student = findUser(username);
        EducationSession session = findSessionById(sessionId);
        EducationSessionParticipant participant = findParticipant(session, student);

        participant.setLastActivityAt(Instant.now());

        return participantRepository.save(participant);
    }

    @Transactional
    public EducationSessionParticipant joinSession(String username,
                                                   String sessionCode,
                                                   String displayName) {
        EducationSession session = findSessionByCode(sessionCode);

        if (session.getStatus() != EducationSessionStatus.ACTIVE) {
            throw conflict("EDUCATION_SESSION_NOT_ACTIVE", "Учебная сессия уже не активна");
        }

        User student = findUser(username);

        if (session.getTeacher().getId().equals(student.getId())) {
            throw forbidden("TEACHER_CANNOT_JOIN_AS_STUDENT", "Преподаватель не может подключиться как ученик");
        }

        return participantRepository.findByEducationSessionAndStudent(session, student)
                .map(existing -> reconnectExistingParticipant(existing, displayName, username))
                .orElseGet(() -> createNewParticipant(session, student, displayName, username));
    }

    private EducationSessionParticipant reconnectExistingParticipant(EducationSessionParticipant participant,
                                                                     String displayName,
                                                                     String username) {
        EducationSession session = participant.getEducationSession();

        if (participant.getStatus() != EducationParticipantStatus.APPROVED) {
            checkStudentLimit(session);
        }

        participant.setDisplayName(resolveDisplayName(displayName, username));
        participant.setStatus(EducationParticipantStatus.WAITING);
        participant.setHasControl(false);
        participant.setControlRequested(false);
        participant.setControlRequestedAt(null);
        participant.setControlGrantedAt(null);
        participant.setScreenShareRequested(false);
        participant.setScreenShareActive(false);
        participant.setScreenShareRequestedAt(null);
        participant.setScreenShareStartedAt(null);
        participant.setApprovedAt(null);
        participant.setJoinedAt(Instant.now());
        participant.setLastActivityAt(Instant.now());

        EducationSessionParticipant saved = participantRepository.save(participant);

        eventService.log(
                session,
                saved.getStudent(),
                EducationSessionEventType.STUDENT_JOINED,
                "Ученик " + saved.getDisplayName() + " повторно отправил заявку на подключение"
        );

        return saved;
    }

    private EducationSessionParticipant createNewParticipant(EducationSession session,
                                                             User student,
                                                             String displayName,
                                                             String username) {
        checkStudentLimit(session);

        EducationSessionParticipant participant = new EducationSessionParticipant();
        participant.setEducationSession(session);
        participant.setStudent(student);
        participant.setDisplayName(resolveDisplayName(displayName, username));
        participant.setStatus(EducationParticipantStatus.WAITING);
        participant.setHasControl(false);
        participant.setControlRequested(false);
        participant.setScreenShareRequested(false);
        participant.setScreenShareActive(false);
        participant.setJoinedAt(Instant.now());
        participant.setLastActivityAt(Instant.now());

        EducationSessionParticipant saved = participantRepository.save(participant);

        eventService.log(
                session,
                student,
                EducationSessionEventType.STUDENT_JOINED,
                "Ученик " + saved.getDisplayName() + " отправил заявку на подключение"
        );

        return saved;
    }

    @Transactional(readOnly = true)
    public List<EducationSessionParticipant> getParticipants(String username, String sessionCode) {
        EducationSession session = findSessionByCode(sessionCode);

        if (!session.getTeacher().getUsername().equals(username)) {
            throw forbidden("ONLY_TEACHER_CAN_VIEW_PARTICIPANTS", "Только преподаватель может видеть список участников");
        }

        return participantRepository.findByEducationSessionOrderByJoinedAtAsc(session);
    }

    @Transactional
    public EducationSessionParticipant approveParticipant(String teacherUsername, Long participantId) {
        EducationSessionParticipant participant = findParticipantById(participantId);
        EducationSession session = participant.getEducationSession();

        if (!session.getTeacher().getUsername().equals(teacherUsername)) {
            throw forbidden("ONLY_TEACHER_CAN_APPROVE_PARTICIPANT", "Только преподаватель может принять участника");
        }

        participant.setStatus(EducationParticipantStatus.APPROVED);
        participant.setApprovedAt(Instant.now());
        participant.setLastActivityAt(Instant.now());

        EducationSessionParticipant saved = participantRepository.save(participant);

        eventService.log(
                session,
                session.getTeacher(),
                EducationSessionEventType.STUDENT_APPROVED,
                "Преподаватель подтвердил ученика " + saved.getDisplayName()
        );

        return saved;
    }

    @Transactional
    public EducationSessionParticipant rejectParticipant(String teacherUsername, Long participantId) {
        EducationSessionParticipant participant = findParticipantById(participantId);
        EducationSession session = participant.getEducationSession();

        if (!session.getTeacher().getUsername().equals(teacherUsername)) {
            throw forbidden("ONLY_TEACHER_CAN_REJECT_PARTICIPANT", "Только преподаватель может отклонить участника");
        }

        participant.setStatus(EducationParticipantStatus.REJECTED);
        participant.setControlRequested(false);
        participant.setHasControl(false);
        participant.setScreenShareRequested(false);
        participant.setScreenShareActive(false);
        participant.setScreenShareRequestedAt(null);
        participant.setScreenShareStartedAt(null);
        participant.setLastActivityAt(Instant.now());

        EducationSessionParticipant saved = participantRepository.save(participant);

        eventService.log(
                session,
                session.getTeacher(),
                EducationSessionEventType.STUDENT_REJECTED,
                "Преподаватель отклонил заявку ученика " + saved.getDisplayName()
        );

        return saved;
    }

    @Transactional
    public EducationSessionParticipant getMyParticipantStatusBySessionCode(String username, String sessionCode) {
        User student = findUser(username);
        EducationSession session = findSessionByCode(sessionCode);
        EducationSessionParticipant participant = findParticipant(session, student);

        participant.setLastActivityAt(Instant.now());

        return participantRepository.save(participant);
    }

    @Transactional
    public Map<String, Object> leaveSession(String username, String sessionCode) {
        User student = findUser(username);
        EducationSession session = findSessionByCode(sessionCode);
        EducationSessionParticipant participant = findParticipant(session, student);

        participant.setStatus(EducationParticipantStatus.LEFT);
        participant.setHasControl(false);
        participant.setControlRequested(false);
        participant.setControlRequestedAt(null);
        participant.setControlGrantedAt(null);
        participant.setScreenShareRequested(false);
        participant.setScreenShareActive(false);
        participant.setScreenShareRequestedAt(null);
        participant.setScreenShareStartedAt(null);
        participant.setLastActivityAt(Instant.now());

        EducationSessionParticipant saved = participantRepository.save(participant);

        eventService.log(
                session,
                student,
                EducationSessionEventType.STUDENT_LEFT,
                "Ученик " + saved.getDisplayName() + " вышел из учебной сессии"
        );

        return toMap(saved);
    }

    private void checkStudentLimit(EducationSession session) {
        long approvedCount = participantRepository.countByEducationSessionAndStatus(
                session,
                EducationParticipantStatus.APPROVED
        );

        if (approvedCount >= session.getMaxStudents()) {
            throw conflict("EDUCATION_SESSION_STUDENT_LIMIT_REACHED", "В сессии уже максимальное количество учеников");
        }
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> notFound("USER_NOT_FOUND", "Пользователь не найден"));
    }

    private EducationSession findSessionById(Long sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> notFound("EDUCATION_SESSION_NOT_FOUND", "Учебная сессия не найдена"));
    }

    private EducationSession findSessionByCode(String sessionCode) {
        return sessionRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> notFound("EDUCATION_SESSION_NOT_FOUND", "Учебная сессия не найдена"));
    }

    private EducationSessionParticipant findParticipant(EducationSession session, User student) {
        return participantRepository.findByEducationSessionAndStudent(session, student)
                .orElseThrow(() -> notFound("EDUCATION_PARTICIPANT_NOT_FOUND", "Участник не найден"));
    }

    private EducationSessionParticipant findParticipantById(Long participantId) {
        return participantRepository.findWithDetailsById(participantId)
                .orElseThrow(() -> notFound("EDUCATION_PARTICIPANT_NOT_FOUND", "Участник не найден"));
    }

    private String resolveDisplayName(String displayName, String username) {
        if (displayName != null && !displayName.isBlank()) {
            return displayName.trim();
        }

        return username;
    }

    private Map<String, Object> toMap(EducationSessionParticipant participant) {
        Map<String, Object> response = new HashMap<>();

        response.put("id", participant.getId());
        response.put("displayName", participant.getDisplayName());
        response.put("username", participant.getStudent().getUsername());
        response.put("studentId", participant.getStudent().getId());
        response.put("status", participant.getStatus());
        response.put("joinedAt", participant.getJoinedAt());
        response.put("approvedAt", participant.getApprovedAt());
        response.put("controlRequested", participant.isControlRequested());
        response.put("hasControl", participant.isHasControl());
        response.put("controlRequestedAt", participant.getControlRequestedAt());
        response.put("controlGrantedAt", participant.getControlGrantedAt());
        response.put("lastActivityAt", participant.getLastActivityAt());
        response.put("screenShareRequested", participant.isScreenShareRequested());
        response.put("screenShareActive", participant.isScreenShareActive());
        response.put("screenShareRequestedAt", participant.getScreenShareRequestedAt());
        response.put("screenShareStartedAt", participant.getScreenShareStartedAt());

        return response;
    }

    private BusinessException forbidden(String code, String message) {
        return new BusinessException(HttpStatus.FORBIDDEN, code, message);
    }

    private BusinessException notFound(String code, String message) {
        return new BusinessException(HttpStatus.NOT_FOUND, code, message);
    }

    private BusinessException conflict(String code, String message) {
        return new BusinessException(HttpStatus.CONFLICT, code, message);
    }

    @Transactional
    public EducationParticipantResponse getMyParticipantStatusResponse(String username, Long sessionId) {
        return toResponse(getMyParticipantStatus(username, sessionId));
    }

    @Transactional
    public EducationParticipantResponse getMyParticipantStatusBySessionCodeResponse(String username, String sessionCode) {
        return toResponse(getMyParticipantStatusBySessionCode(username, sessionCode));
    }

    @Transactional
    public EducationParticipantResponse joinSessionResponse(String username,
                                                            String sessionCode,
                                                            String displayName) {
        return toResponse(joinSession(username, sessionCode, displayName));
    }

    @Transactional(readOnly = true)
    public List<EducationParticipantResponse> getParticipantResponses(String username, String sessionCode) {
        return getParticipants(username, sessionCode)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public EducationParticipantResponse approveParticipantResponse(String username, Long participantId) {
        return toResponse(approveParticipant(username, participantId));
    }

    @Transactional
    public EducationParticipantResponse rejectParticipantResponse(String username, Long participantId) {
        return toResponse(rejectParticipant(username, participantId));
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