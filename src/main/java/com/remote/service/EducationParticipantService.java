package com.remote.service;

import com.remote.model.*;
import com.remote.repository.EducationSessionParticipantRepository;
import com.remote.repository.EducationSessionRepository;
import com.remote.repository.UserRepository;
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
    private final EducationSessionRepository educationSessionRepository;
    private final EducationSessionParticipantRepository participantRepository;
    private final UserRepository userRepository;

    public EducationParticipantService(EducationSessionRepository sessionRepository, EducationSessionRepository educationSessionRepository,
                                       EducationSessionParticipantRepository participantRepository,
                                       UserRepository userRepository) {
        this.sessionRepository = sessionRepository;
        this.educationSessionRepository = educationSessionRepository;
        this.participantRepository = participantRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public EducationSessionParticipant getMyParticipantStatus(String username, Long sessionId) {
        User student = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        EducationSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Учебная сессия не найдена"));

        return participantRepository.findByEducationSessionAndStudent(session, student)
                .orElseThrow(() -> new IllegalArgumentException("Участник не найден"));
    }

    @Transactional
    public EducationSessionParticipant joinSession(String username,
                                                   String sessionCode,
                                                   String displayName) {
        EducationSession session = educationSessionRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> new IllegalArgumentException("Учебная сессия не найдена"));

        if (session.getStatus() != EducationSessionStatus.ACTIVE) {
            throw new IllegalArgumentException("Учебная сессия уже не активна");
        }

        User student = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        if (session.getTeacher().getId().equals(student.getId())) {
            throw new IllegalArgumentException("Преподаватель не может подключиться как ученик");
        }

        long approvedCount = participantRepository.countByEducationSessionAndStatus(
                session,
                EducationParticipantStatus.APPROVED
        );

        if (approvedCount >= session.getMaxStudents()) {
            throw new IllegalArgumentException("В сессии уже максимальное количество учеников");
        }

        return participantRepository.findByEducationSessionAndStudent(session, student)
                .map(existing -> {
                    existing.setDisplayName(resolveDisplayName(displayName, username));
                    existing.setStatus(EducationParticipantStatus.WAITING);
                    return participantRepository.save(existing);
                })
                .orElseGet(() -> {
                    EducationSessionParticipant participant = new EducationSessionParticipant();
                    participant.setEducationSession(session);
                    participant.setStudent(student);
                    participant.setDisplayName(resolveDisplayName(displayName, username));
                    participant.setStatus(EducationParticipantStatus.WAITING);
                    return participantRepository.save(participant);
                });
    }

    @Transactional(readOnly = true)
    public List<EducationSessionParticipant> getParticipants(String username, String sessionCode) {
        EducationSession session = educationSessionRepository.findBySessionCode(sessionCode)
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

        return participantRepository.save(participant);
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

        return participantRepository.save(participant);
    }

    private void checkTeacher(String username, EducationSessionParticipant participant) {
        String teacherUsername = participant.getEducationSession().getTeacher().getUsername();

        if (!teacherUsername.equals(username)) {
            throw new IllegalArgumentException("Действие доступно только преподавателю");
        }
    }

    private String resolveDisplayName(String displayName, String username) {
        if (displayName != null && !displayName.isBlank()) {
            return displayName.trim();
        }

        return username;
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

        return participantRepository.save(participant);
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

        for (EducationSessionParticipant active : participantRepository.findByEducationSessionAndHasControlTrue(session)) {
            active.setHasControl(false);
            active.setControlRequested(false);
            participantRepository.save(active);
        }

        participant.setHasControl(true);
        participant.setControlRequested(false);
        participant.setControlGrantedAt(LocalDateTime.now());

        return participantRepository.save(participant);
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

        return participantRepository.save(participant);
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

        return participantRepository.save(participant);
    }

    @Transactional(readOnly = true)
    public boolean hasControlInSession(String sessionCode) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

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
    public Map<String, Object> leaveSession(String sessionCode) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        User student = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        EducationSession session = sessionRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> new IllegalArgumentException("Учебная сессия не найдена"));

        EducationSessionParticipant participant = participantRepository
                .findByEducationSessionAndStudent(session, student)
                .orElseThrow(() -> new IllegalArgumentException("Участник не найден"));

        participant.setStatus(EducationParticipantStatus.DISCONNECTED);
        participant.setHasControl(false);
        participant.setControlRequested(false);

        EducationSessionParticipant saved = participantRepository.save(participant);

        Map<String, Object> response = new HashMap<>();
        response.put("id", saved.getId());
        response.put("displayName", saved.getDisplayName());
        response.put("username", saved.getStudent().getUsername());
        response.put("status", saved.getStatus());
        response.put("joinedAt", saved.getJoinedAt());
        response.put("approvedAt", saved.getApprovedAt());
        response.put("controlRequested", saved.isControlRequested());
        response.put("hasControl", saved.isHasControl());
        response.put("controlRequestedAt", saved.getControlRequestedAt());
        response.put("controlGrantedAt", saved.getControlGrantedAt());

        return response;
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

}