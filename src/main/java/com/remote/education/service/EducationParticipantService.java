package com.remote.education.service;

import com.remote.core.model.User;
import com.remote.education.model.*;
import com.remote.education.repository.EducationSessionParticipantRepository;
import com.remote.education.repository.EducationSessionRepository;
import com.remote.core.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
        User student = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        EducationSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Учебная сессия не найдена"));

        EducationSessionParticipant participant = participantRepository.findByEducationSessionAndStudent(session, student)
                .orElseThrow(() -> new IllegalArgumentException("Участник не найден"));

        participant.setLastActivityAt(LocalDateTime.now());

        return participantRepository.save(participant);
    }

    @Transactional
    public EducationSessionParticipant joinSession(String username,
                                                   String sessionCode,
                                                   String displayName) {
        EducationSession session = sessionRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> new IllegalArgumentException("Учебная сессия не найдена"));

        if (session.getStatus() != EducationSessionStatus.ACTIVE) {
            throw new IllegalArgumentException("Учебная сессия уже не активна");
        }

        User student = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        if (session.getTeacher().getId().equals(student.getId())) {
            throw new IllegalArgumentException("Преподаватель не может подключиться как ученик");
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
            long approvedCount = participantRepository.countByEducationSessionAndStatus(
                    session,
                    EducationParticipantStatus.APPROVED
            );

            if (approvedCount >= session.getMaxStudents()) {
                throw new IllegalArgumentException("В сессии уже максимальное количество учеников");
            }
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
        participant.setJoinedAt(LocalDateTime.now());
        participant.setLastActivityAt(LocalDateTime.now());

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
        long approvedCount = participantRepository.countByEducationSessionAndStatus(
                session,
                EducationParticipantStatus.APPROVED
        );

        if (approvedCount >= session.getMaxStudents()) {
            throw new IllegalArgumentException("В сессии уже максимальное количество учеников");
        }

        EducationSessionParticipant participant = new EducationSessionParticipant();
        participant.setEducationSession(session);
        participant.setStudent(student);
        participant.setDisplayName(resolveDisplayName(displayName, username));
        participant.setStatus(EducationParticipantStatus.WAITING);
        participant.setHasControl(false);
        participant.setControlRequested(false);
        participant.setScreenShareRequested(false);
        participant.setScreenShareActive(false);
        participant.setJoinedAt(LocalDateTime.now());
        participant.setLastActivityAt(LocalDateTime.now());

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
        EducationSession session = sessionRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> new IllegalArgumentException("Учебная сессия не найдена"));

        if (!session.getTeacher().getUsername().equals(username)) {
            throw new IllegalArgumentException("Только преподаватель может видеть список участников");
        }

        return participantRepository.findByEducationSessionOrderByJoinedAtAsc(session);
    }

    @Transactional
    public EducationSessionParticipant approveParticipant(String teacherUsername, Long participantId) {
        EducationSessionParticipant participant = participantRepository.findWithDetailsById(participantId)
                .orElseThrow(() -> new IllegalArgumentException("Участник не найден"));

        EducationSession session = participant.getEducationSession();

        if (!session.getTeacher().getUsername().equals(teacherUsername)) {
            throw new IllegalArgumentException("Только преподаватель может принять участника");
        }

        participant.setStatus(EducationParticipantStatus.APPROVED);
        participant.setApprovedAt(LocalDateTime.now());
        participant.setLastActivityAt(LocalDateTime.now());

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
        EducationSessionParticipant participant = participantRepository.findWithDetailsById(participantId)
                .orElseThrow(() -> new IllegalArgumentException("Участник не найден"));

        EducationSession session = participant.getEducationSession();

        if (!session.getTeacher().getUsername().equals(teacherUsername)) {
            throw new IllegalArgumentException("Только преподаватель может отклонить участника");
        }

        participant.setStatus(EducationParticipantStatus.REJECTED);
        participant.setControlRequested(false);
        participant.setHasControl(false);
        participant.setScreenShareRequested(false);
        participant.setScreenShareActive(false);
        participant.setScreenShareRequestedAt(null);
        participant.setScreenShareStartedAt(null);
        participant.setLastActivityAt(LocalDateTime.now());

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
        participant.setControlRequestedAt(LocalDateTime.now());
        participant.setLastActivityAt(LocalDateTime.now());

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
            active.setLastActivityAt(LocalDateTime.now());
            participantRepository.save(active);
        }

        participant.setHasControl(true);
        participant.setControlRequested(false);
        participant.setControlGrantedAt(LocalDateTime.now());
        participant.setLastActivityAt(LocalDateTime.now());

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
        participant.setLastActivityAt(LocalDateTime.now());

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
        participant.setLastActivityAt(LocalDateTime.now());

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
    public EducationSessionParticipant getMyParticipantStatusBySessionCode(String username, String sessionCode) {
        User student = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        EducationSession session = sessionRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> new IllegalArgumentException("Учебная сессия не найдена"));

        EducationSessionParticipant participant = participantRepository
                .findByEducationSessionAndStudent(session, student)
                .orElseThrow(() -> new IllegalArgumentException("Участник не найден"));

        participant.setLastActivityAt(LocalDateTime.now());

        return participantRepository.save(participant);
    }

    @Transactional
    public Map<String, Object> leaveSession(String username, String sessionCode) {
        User student = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        EducationSession session = sessionRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> new IllegalArgumentException("Учебная сессия не найдена"));

        EducationSessionParticipant participant = participantRepository
                .findByEducationSessionAndStudent(session, student)
                .orElseThrow(() -> new IllegalArgumentException("Участник не найден"));

        participant.setStatus(EducationParticipantStatus.LEFT);
        participant.setHasControl(false);
        participant.setControlRequested(false);
        participant.setControlRequestedAt(null);
        participant.setControlGrantedAt(null);
        participant.setScreenShareRequested(false);
        participant.setScreenShareActive(false);
        participant.setScreenShareRequestedAt(null);
        participant.setScreenShareStartedAt(null);
        participant.setLastActivityAt(LocalDateTime.now());

        EducationSessionParticipant saved = participantRepository.save(participant);

        eventService.log(
                session,
                student,
                EducationSessionEventType.STUDENT_LEFT,
                "Ученик " + saved.getDisplayName() + " вышел из учебной сессии"
        );

        return toMap(saved);
    }


    @Transactional
    public EducationSessionParticipant requestScreenShare(String username, String sessionCode) {
        User student = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        EducationSession session = sessionRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> new IllegalArgumentException("Учебная сессия не найдена"));

        if (!session.getAllowStudentScreenShare()) {
            throw new IllegalArgumentException("В этой сессии демонстрация экрана учениками запрещена");
        }

        EducationSessionParticipant participant = participantRepository
                .findByEducationSessionAndStudent(session, student)
                .orElseThrow(() -> new IllegalArgumentException("Вы не являетесь участником этой сессии"));

        if (participant.getStatus() != EducationParticipantStatus.APPROVED) {
            throw new IllegalArgumentException("Демонстрацию может запросить только подтверждённый ученик");
        }

        participant.setScreenShareRequested(true);
        participant.setScreenShareActive(false);
        participant.setScreenShareRequestedAt(LocalDateTime.now());
        participant.setScreenShareStartedAt(null);
        participant.setLastActivityAt(LocalDateTime.now());

        EducationSessionParticipant saved = participantRepository.save(participant);

        eventService.log(
                session,
                student,
                EducationSessionEventType.SCREEN_SHARE_REQUESTED,
                "Ученик " + saved.getDisplayName() + " запросил демонстрацию своего экрана"
        );

        return saved;
    }

    @Transactional
    public EducationSessionParticipant grantScreenShare(String teacherUsername, Long participantId) {
        EducationSessionParticipant participant = participantRepository.findWithDetailsById(participantId)
                .orElseThrow(() -> new IllegalArgumentException("Участник не найден"));

        EducationSession session = participant.getEducationSession();

        if (!session.getTeacher().getUsername().equals(teacherUsername)) {
            throw new IllegalArgumentException("Только преподаватель может разрешить демонстрацию экрана");
        }

        if (!session.getAllowStudentScreenShare()) {
            throw new IllegalArgumentException("В этой сессии демонстрация экрана учениками запрещена");
        }

        if (participant.getStatus() != EducationParticipantStatus.APPROVED) {
            throw new IllegalArgumentException("Демонстрацию можно разрешить только подтверждённому ученику");
        }

        for (EducationSessionParticipant active : participantRepository.findByEducationSessionOrderByJoinedAtAsc(session)) {
            if (active.isScreenShareActive() || active.isScreenShareRequested()) {
                active.setScreenShareActive(false);
                active.setScreenShareRequested(false);
                active.setScreenShareStartedAt(null);
                active.setLastActivityAt(LocalDateTime.now());
                participantRepository.save(active);
            }
        }

        participant.setScreenShareRequested(false);
        participant.setScreenShareActive(true);
        participant.setScreenShareStartedAt(LocalDateTime.now());
        participant.setLastActivityAt(LocalDateTime.now());

        EducationSessionParticipant saved = participantRepository.save(participant);

        eventService.log(
                session,
                session.getTeacher(),
                EducationSessionEventType.SCREEN_SHARE_GRANTED,
                "Преподаватель включил демонстрацию экрана ученика " + saved.getDisplayName()
        );

        return saved;
    }

    @Transactional
    public EducationSessionParticipant rejectScreenShare(String teacherUsername, Long participantId) {
        EducationSessionParticipant participant = participantRepository.findWithDetailsById(participantId)
                .orElseThrow(() -> new IllegalArgumentException("Участник не найден"));

        EducationSession session = participant.getEducationSession();

        if (!session.getTeacher().getUsername().equals(teacherUsername)) {
            throw new IllegalArgumentException("Только преподаватель может отклонить демонстрацию экрана");
        }

        participant.setScreenShareRequested(false);
        participant.setScreenShareActive(false);
        participant.setScreenShareRequestedAt(null);
        participant.setScreenShareStartedAt(null);
        participant.setLastActivityAt(LocalDateTime.now());

        EducationSessionParticipant saved = participantRepository.save(participant);

        eventService.log(
                session,
                session.getTeacher(),
                EducationSessionEventType.SCREEN_SHARE_REJECTED,
                "Преподаватель отклонил демонстрацию экрана ученика " + saved.getDisplayName()
        );

        return saved;
    }

    @Transactional
    public EducationSessionParticipant stopScreenShare(String teacherUsername, Long participantId) {
        EducationSessionParticipant participant = participantRepository.findWithDetailsById(participantId)
                .orElseThrow(() -> new IllegalArgumentException("Участник не найден"));

        EducationSession session = participant.getEducationSession();

        if (!session.getTeacher().getUsername().equals(teacherUsername)) {
            throw new IllegalArgumentException("Только преподаватель может остановить демонстрацию экрана");
        }

        participant.setScreenShareRequested(false);
        participant.setScreenShareActive(false);
        participant.setScreenShareRequestedAt(null);
        participant.setScreenShareStartedAt(null);
        participant.setLastActivityAt(LocalDateTime.now());

        EducationSessionParticipant saved = participantRepository.save(participant);

        eventService.log(
                session,
                session.getTeacher(),
                EducationSessionEventType.SCREEN_SHARE_STOPPED,
                "Преподаватель остановил демонстрацию экрана ученика " + saved.getDisplayName()
        );

        return saved;
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

    @Transactional
    public EducationSessionParticipant stopMyScreenShare(String username, String sessionCode) {
        User student = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        EducationSession session = sessionRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> new IllegalArgumentException("Учебная сессия не найдена"));

        EducationSessionParticipant participant = participantRepository
                .findByEducationSessionAndStudent(session, student)
                .orElseThrow(() -> new IllegalArgumentException("Участник не найден"));

        participant.setScreenShareRequested(false);
        participant.setScreenShareActive(false);
        participant.setScreenShareRequestedAt(null);
        participant.setScreenShareStartedAt(null);
        participant.setLastActivityAt(LocalDateTime.now());

        EducationSessionParticipant saved = participantRepository.save(participant);

        eventService.log(
                session,
                student,
                EducationSessionEventType.SCREEN_SHARE_STOPPED,
                "Ученик " + saved.getDisplayName() + " остановил демонстрацию своего экрана"
        );

        return saved;
    }
}