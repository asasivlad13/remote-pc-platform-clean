package com.remote.service;

import com.remote.controller.EducationSessionController;
import com.remote.model.*;
import com.remote.repository.EducationSessionRepository;
import com.remote.repository.PcRepository;
import com.remote.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.remote.repository.EducationSessionParticipantRepository;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;

@Service
public class EducationSessionService {

    private final EducationSessionRepository educationSessionRepository;
    private final UserRepository userRepository;
    private final PcRepository pcRepository;

    private final EducationSessionParticipantRepository participantRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public EducationSessionService(EducationSessionRepository educationSessionRepository,
                                   UserRepository userRepository,
                                   PcRepository pcRepository, EducationSessionParticipantRepository participantRepository) {
        this.educationSessionRepository = educationSessionRepository;
        this.userRepository = userRepository;
        this.pcRepository = pcRepository;
        this.participantRepository = participantRepository;
    }

    @Transactional
    public EducationSession createSession(String username,
                                          Long teacherPcId,
                                          String title,
                                          Integer maxStudents,
                                          Boolean allowStudentControl,
                                          Boolean allowFileTransfer,
                                          Boolean allowStudentScreenShare) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Session title is required");
        }

        User teacher = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Teacher not found"));

        Pc teacherPc = pcRepository.findById(teacherPcId)
                .orElseThrow(() -> new IllegalArgumentException("Teacher PC not found"));

        if (teacherPc.getUser() == null || !teacherPc.getUser().getId().equals(teacher.getId())) {
            throw new IllegalArgumentException("Selected PC does not belong to current user");
        }

        EducationSession session = new EducationSession();
        session.setSessionCode(generateUniqueCode());
        session.setTitle(title.trim());
        session.setTeacher(teacher);
        session.setTeacherPc(teacherPc);
        session.setMaxStudents(maxStudents != null && maxStudents > 0 ? maxStudents : 30);
        session.setAllowStudentControl(Boolean.TRUE.equals(allowStudentControl));
        session.setAllowFileTransfer(Boolean.TRUE.equals(allowFileTransfer));
        session.setAllowStudentScreenShare(Boolean.TRUE.equals(allowStudentScreenShare));
        session.setStatus(EducationSessionStatus.ACTIVE);

        EducationSession saved = educationSessionRepository.save(session);
        initializeForResponse(saved);

        return saved;
    }

    @Transactional(readOnly = true)
    public List<EducationSession> getMySessions(String username) {
        User teacher = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        List<EducationSession> sessions = educationSessionRepository.findByTeacherOrderByCreatedAtDesc(teacher);
        sessions.forEach(this::initializeForResponse);

        return sessions;
    }

    @Transactional(readOnly = true)
    public EducationSession getByCode(String sessionCode) {
        EducationSession session = educationSessionRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> new IllegalArgumentException("Education session not found"));

        initializeForResponse(session);

        return session;
    }

    @Transactional
    public EducationSession finishSession(String username, String sessionCode) {
        EducationSession session = educationSessionRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> new IllegalArgumentException("Education session not found"));

        if (!session.getTeacher().getUsername().equals(username)) {
            throw new IllegalArgumentException("Only teacher can finish this session");
        }

        session.finish();

        EducationSession saved = educationSessionRepository.save(session);
        initializeForResponse(saved);

        return saved;
    }

    private void initializeForResponse(EducationSession session) {
        if (session == null) {
            return;
        }

        if (session.getTeacher() != null) {
            session.getTeacher().getUsername();
        }

        if (session.getTeacherPc() != null) {
            session.getTeacherPc().getId();
            session.getTeacherPc().getName();
            session.getTeacherPc().getWebrtcUrl();
            session.getTeacherPc().getStreamName();
            session.getTeacherPc().getScreenWidth();
            session.getTeacherPc().getScreenHeight();
        }
    }

    private String generateUniqueCode() {
        String code;

        do {
            code = String.valueOf(100000 + secureRandom.nextInt(900000));
        } while (educationSessionRepository.existsBySessionCode(code));

        return code;
    }

    @Transactional(readOnly = true)
    public Optional<EducationSessionController.EducationSessionResponse> getMyActiveTeacherSession() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        User teacher = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        return educationSessionRepository
                .findFirstByTeacherAndStatusOrderByCreatedAtDesc(teacher, EducationSessionStatus.ACTIVE)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Optional<EducationSessionController.EducationSessionResponse> getMyActiveStudentSession() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        User student = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        return participantRepository
                .findFirstByStudentAndStatusInOrderByJoinedAtDesc(
                        student,
                        List.of(
                                EducationParticipantStatus.WAITING,
                                EducationParticipantStatus.APPROVED
                        )
                )
                .map(EducationSessionParticipant::getEducationSession)
                .filter(session -> session.getStatus() == EducationSessionStatus.ACTIVE)
                .map(this::toResponse);
    }

    private EducationSessionController.EducationSessionResponse toResponse(EducationSession session) {
        initializeForResponse(session);

        return new EducationSessionController.EducationSessionResponse(
                session.getId(),
                session.getSessionCode(),
                session.getTitle(),
                session.getStatus().name(),
                session.getTeacher().getUsername(),
                session.getTeacherPc().getId(),
                session.getTeacherPc().getName(),
                session.getTeacherPc().getWebrtcUrl(),
                session.getTeacherPc().getStreamName(),
                session.getTeacherPc().getScreenWidth(),
                session.getTeacherPc().getScreenHeight(),
                session.getMaxStudents(),
                session.getAllowStudentControl(),
                session.getAllowFileTransfer(),
                session.getAllowStudentScreenShare(),
                session.getCreatedAt(),
                session.getFinishedAt()
        );
    }
}