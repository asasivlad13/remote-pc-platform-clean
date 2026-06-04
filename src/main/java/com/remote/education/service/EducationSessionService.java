package com.remote.education.service;

import com.remote.core.exception.BusinessException;
import com.remote.core.model.User;
import com.remote.core.repository.UserRepository;
import com.remote.education.dto.EducationSessionResponse;
import com.remote.education.model.EducationParticipantStatus;
import com.remote.education.model.EducationSession;
import com.remote.education.model.EducationSessionParticipant;
import com.remote.education.model.EducationSessionStatus;
import com.remote.education.repository.EducationSessionParticipantRepository;
import com.remote.education.repository.EducationSessionRepository;
import com.remote.pc.model.Pc;
import com.remote.pc.repository.PcRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                                   PcRepository pcRepository,
                                   EducationSessionParticipantRepository participantRepository) {
        this.educationSessionRepository = educationSessionRepository;
        this.userRepository = userRepository;
        this.pcRepository = pcRepository;
        this.participantRepository = participantRepository;
    }

    @Transactional
    public EducationSession createSession(String username,
                                          Long teacherPcId,
                                          String title,
                                          String teacherDisplayName,
                                          Integer maxStudents,
                                          Boolean allowStudentControl,
                                          Boolean allowFileTransfer,
                                          Boolean allowStudentScreenShare) {
        if (title == null || title.isBlank()) {
            throw badRequest("EDUCATION_SESSION_TITLE_REQUIRED", "Название учебной сессии обязательно");
        }

        User teacher = findUser(username);

        Pc teacherPc = pcRepository.findById(teacherPcId)
                .orElseThrow(() -> notFound("TEACHER_PC_NOT_FOUND", "ПК преподавателя не найден"));

        if (teacherPc.getUser() == null || !teacherPc.getUser().getId().equals(teacher.getId())) {
            throw forbidden("TEACHER_PC_ACCESS_DENIED", "Выбранный ПК не принадлежит текущему пользователю");
        }

        EducationSession session = new EducationSession();
        session.setSessionCode(generateUniqueCode());
        session.setTitle(title.trim());
        session.setTeacher(teacher);
        session.setTeacherDisplayName(resolveDisplayName(teacherDisplayName, username));
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

    @Transactional
    public EducationSessionResponse createSessionResponse(String username,
                                                          Long teacherPcId,
                                                          String title,
                                                          String teacherDisplayName,
                                                          Integer maxStudents,
                                                          Boolean allowStudentControl,
                                                          Boolean allowFileTransfer,
                                                          Boolean allowStudentScreenShare) {
        EducationSession session = createSession(
                username,
                teacherPcId,
                title,
                teacherDisplayName,
                maxStudents,
                allowStudentControl,
                allowFileTransfer,
                allowStudentScreenShare
        );

        return toResponse(session);
    }

    @Transactional(readOnly = true)
    public List<EducationSession> getMySessions(String username) {
        User teacher = findUser(username);

        List<EducationSession> sessions = educationSessionRepository.findByTeacherOrderByCreatedAtDesc(teacher);
        sessions.forEach(this::initializeForResponse);

        return sessions;
    }

    @Transactional(readOnly = true)
    public List<EducationSessionResponse> getMySessionResponses(String username) {
        return getMySessions(username)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public EducationSession getByCode(String sessionCode) {
        EducationSession session = findSession(sessionCode);
        initializeForResponse(session);
        return session;
    }

    @Transactional(readOnly = true)
    public EducationSessionResponse getByCodeResponse(String sessionCode) {
        return toResponse(getByCode(sessionCode));
    }

    @Transactional
    public EducationSession finishSession(String username, String sessionCode) {
        EducationSession session = findSession(sessionCode);

        if (session.getTeacher() == null || !session.getTeacher().getUsername().equals(username)) {
            throw forbidden("ONLY_TEACHER_CAN_FINISH_SESSION", "Только преподаватель может завершить эту сессию");
        }

        if (session.getStatus() != EducationSessionStatus.FINISHED) {
            session.finish();
        }

        EducationSession saved = educationSessionRepository.save(session);
        initializeForResponse(saved);

        return saved;
    }

    @Transactional
    public EducationSessionResponse finishSessionResponse(String username, String sessionCode) {
        return toResponse(finishSession(username, sessionCode));
    }

    @Transactional(readOnly = true)
    public Optional<EducationSessionResponse> getMyActiveTeacherSession() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        User teacher = findUser(username);

        return educationSessionRepository
                .findFirstByTeacherAndStatusOrderByCreatedAtDesc(teacher, EducationSessionStatus.ACTIVE)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Optional<EducationSessionResponse> getMyActiveStudentSession() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        User student = findUser(username);

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
                .filter(session -> session.getTeacher() == null
                        || !session.getTeacher().getId().equals(student.getId()))
                .map(this::toResponse);
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> notFound("USER_NOT_FOUND", "Пользователь не найден"));
    }

    private EducationSession findSession(String sessionCode) {
        return educationSessionRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> notFound("EDUCATION_SESSION_NOT_FOUND", "Учебная сессия не найдена"));
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

    private EducationSessionResponse toResponse(EducationSession session) {
        initializeForResponse(session);

        return new EducationSessionResponse(
                session.getId(),
                session.getSessionCode(),
                session.getTitle(),
                session.getStatus().name(),
                session.getTeacher() != null ? session.getTeacher().getUsername() : null,
                resolveDisplayName(
                        session.getTeacherDisplayName(),
                        session.getTeacher() != null ? session.getTeacher().getUsername() : null
                ),
                session.getTeacherPc() != null ? session.getTeacherPc().getId() : null,
                session.getTeacherPc() != null ? session.getTeacherPc().getName() : null,
                session.getTeacherPc() != null ? session.getTeacherPc().getWebrtcUrl() : null,
                session.getTeacherPc() != null ? session.getTeacherPc().getStreamName() : null,
                session.getTeacherPc() != null ? session.getTeacherPc().getScreenWidth() : null,
                session.getTeacherPc() != null ? session.getTeacherPc().getScreenHeight() : null,
                session.getMaxStudents(),
                session.getAllowStudentControl(),
                session.getAllowFileTransfer(),
                session.getAllowStudentScreenShare(),
                session.getCreatedAt(),
                session.getFinishedAt()
        );
    }

    private String resolveDisplayName(String displayName, String fallbackUsername) {
        if (displayName != null && !displayName.isBlank()) {
            String normalized = displayName.trim();

            if (normalized.length() <= 100) {
                return normalized;
            }

            return normalized.substring(0, 100);
        }

        if (fallbackUsername != null && !fallbackUsername.isBlank()) {
            return fallbackUsername;
        }

        return "Пользователь";
    }

    private BusinessException badRequest(String code, String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, code, message);
    }

    private BusinessException forbidden(String code, String message) {
        return new BusinessException(HttpStatus.FORBIDDEN, code, message);
    }

    private BusinessException notFound(String code, String message) {
        return new BusinessException(HttpStatus.NOT_FOUND, code, message);
    }
}