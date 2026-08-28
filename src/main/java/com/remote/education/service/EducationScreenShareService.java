package com.remote.education.service;

import com.remote.core.model.User;
import com.remote.core.repository.UserRepository;
import com.remote.education.dto.ActiveScreenShareResponse;
import com.remote.education.dto.EducationAgentResponse;
import com.remote.education.dto.EducationParticipantResponse;
import com.remote.education.model.EducationParticipantStatus;
import com.remote.education.model.EducationSession;
import com.remote.education.model.EducationSessionEventType;
import com.remote.education.model.EducationSessionParticipant;
import com.remote.education.repository.EducationSessionParticipantRepository;
import com.remote.education.repository.EducationSessionRepository;
import com.remote.pc.model.Pc;
import com.remote.pc.repository.PcRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class EducationScreenShareService {

    private final PcRepository pcRepository;
    private final UserRepository userRepository;
    private final EducationSessionRepository sessionRepository;
    private final EducationSessionParticipantRepository participantRepository;
    private final EducationSessionEventService eventService;

    public EducationScreenShareService(PcRepository pcRepository,
                                       UserRepository userRepository,
                                       EducationSessionRepository sessionRepository,
                                       EducationSessionParticipantRepository participantRepository,
                                       EducationSessionEventService eventService) {
        this.pcRepository = pcRepository;
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.eventService = eventService;
    }

    @Transactional(readOnly = true)
    public EducationAgentResponse getMyAgent(String username) {
        User user = findUser(username);

        return pcRepository.findByUser(user)
                .stream()
                .findFirst()
                .map(this::toAgentResponse)
                .orElseGet(this::noAgentResponse);
    }

    @Transactional(readOnly = true)
    public EducationAgentResponse getParticipantAgent(Long participantId, String teacherUsername) {
        User teacher = findUser(teacherUsername);

        EducationSessionParticipant participant = participantRepository.findWithDetailsById(participantId)
                .orElseThrow(() -> new IllegalArgumentException("Участник не найден"));

        if (!participant.getEducationSession().getTeacher().getId().equals(teacher.getId())) {
            throw new IllegalArgumentException("Только преподаватель может смотреть экран ученика");
        }

        return pcRepository.findByUser(participant.getStudent())
                .stream()
                .findFirst()
                .map(this::toAgentResponse)
                .orElseGet(this::noAgentResponse);
    }

    @Transactional(readOnly = true)
    public ActiveScreenShareResponse getActiveScreenShare(String sessionCode, String username) {
        User currentUser = findUser(username);
        EducationSession session = findSession(sessionCode);

        boolean isTeacher = session.getTeacher().getId().equals(currentUser.getId());

        boolean isApprovedStudent = participantRepository
                .findByEducationSessionAndStudent(session, currentUser)
                .map(participant -> participant.getStatus() == EducationParticipantStatus.APPROVED)
                .orElse(false);

        if (!isTeacher && !isApprovedStudent) {
            throw new IllegalArgumentException("Нет доступа к демонстрации этой сессии");
        }

        EducationSessionParticipant activeParticipant = participantRepository
                .findByEducationSessionOrderByJoinedAtAsc(session)
                .stream()
                .filter(EducationSessionParticipant::isScreenShareActive)
                .findFirst()
                .orElse(null);

        if (activeParticipant == null) {
            return noActiveScreenShareResponse();
        }

        return activeScreenShareResponse(activeParticipant);
    }

    @Transactional
    public EducationSessionParticipant requestScreenShare(String username, String sessionCode) {
        User student = findUser(username);
        EducationSession session = findSession(sessionCode);

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
        participant.setScreenShareRequestedAt(Instant.now());
        participant.setScreenShareStartedAt(null);
        participant.setLastActivityAt(Instant.now());

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
                active.setLastActivityAt(Instant.now());
                participantRepository.save(active);
            }
        }

        participant.setScreenShareRequested(false);
        participant.setScreenShareActive(true);
        participant.setScreenShareStartedAt(Instant.now());
        participant.setLastActivityAt(Instant.now());

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
        participant.setLastActivityAt(Instant.now());

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
        participant.setLastActivityAt(Instant.now());

        EducationSessionParticipant saved = participantRepository.save(participant);

        eventService.log(
                session,
                session.getTeacher(),
                EducationSessionEventType.SCREEN_SHARE_STOPPED,
                "Преподаватель остановил демонстрацию экрана ученика " + saved.getDisplayName()
        );

        return saved;
    }

    @Transactional
    public EducationSessionParticipant stopMyScreenShare(String username, String sessionCode) {
        User student = findUser(username);
        EducationSession session = findSession(sessionCode);

        EducationSessionParticipant participant = participantRepository
                .findByEducationSessionAndStudent(session, student)
                .orElseThrow(() -> new IllegalArgumentException("Участник не найден"));

        participant.setScreenShareRequested(false);
        participant.setScreenShareActive(false);
        participant.setScreenShareRequestedAt(null);
        participant.setScreenShareStartedAt(null);
        participant.setLastActivityAt(Instant.now());

        EducationSessionParticipant saved = participantRepository.save(participant);

        eventService.log(
                session,
                student,
                EducationSessionEventType.SCREEN_SHARE_STOPPED,
                "Ученик " + saved.getDisplayName() + " остановил демонстрацию своего экрана"
        );

        return saved;
    }

    @Transactional
    public EducationParticipantResponse stopMyScreenShareResponse(String username, String sessionCode) {
        return toResponse(stopMyScreenShare(username, sessionCode));
    }

    @Transactional
    public EducationParticipantResponse requestScreenShareResponse(String username, String sessionCode) {
        return toResponse(requestScreenShare(username, sessionCode));
    }

    @Transactional
    public EducationParticipantResponse grantScreenShareResponse(String username, Long participantId) {
        return toResponse(grantScreenShare(username, participantId));
    }

    @Transactional
    public EducationParticipantResponse rejectScreenShareResponse(String username, Long participantId) {
        return toResponse(rejectScreenShare(username, participantId));
    }

    @Transactional
    public EducationParticipantResponse stopScreenShareResponse(String username, Long participantId) {
        return toResponse(stopScreenShare(username, participantId));
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
    }

    private EducationSession findSession(String sessionCode) {
        return sessionRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> new IllegalArgumentException("Учебная сессия не найдена"));
    }

    private ActiveScreenShareResponse activeScreenShareResponse(EducationSessionParticipant activeParticipant) {
        return new ActiveScreenShareResponse(
                true,
                activeParticipant.getId(),
                activeParticipant.getStudent().getId(),
                activeParticipant.getDisplayName(),
                pcRepository.findByUser(activeParticipant.getStudent())
                        .stream()
                        .findFirst()
                        .map(this::toAgentResponse)
                        .orElseGet(this::noAgentResponse)
        );
    }

    private ActiveScreenShareResponse noActiveScreenShareResponse() {
        return new ActiveScreenShareResponse(
                false,
                null,
                null,
                null,
                null
        );
    }

    private EducationAgentResponse toAgentResponse(Pc pc) {
        boolean online = pc.getStatus() != null
                && "ONLINE".equalsIgnoreCase(pc.getStatus().name());

        return new EducationAgentResponse(
                true,
                pc.getId(),
                pc.getName(),
                pc.getStatus() != null ? pc.getStatus().name() : "UNKNOWN",
                online,
                pc.getWebrtcUrl(),
                pc.getStreamName(),
                pc.getScreenWidth(),
                pc.getScreenHeight()
        );
    }

    private EducationAgentResponse noAgentResponse() {
        return new EducationAgentResponse(
                false,
                null,
                null,
                "NO_AGENT",
                false,
                null,
                null,
                null,
                null
        );
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