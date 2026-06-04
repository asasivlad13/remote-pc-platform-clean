package com.remote.education.service;

import com.remote.core.model.User;
import com.remote.core.repository.UserRepository;
import com.remote.education.dto.EducationSessionEventResponse;
import com.remote.education.model.EducationSession;
import com.remote.education.model.EducationSessionEvent;
import com.remote.education.model.EducationSessionEventType;
import com.remote.education.repository.EducationSessionEventRepository;
import com.remote.education.repository.EducationSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EducationSessionEventService {

    private final EducationSessionEventRepository eventRepository;
    private final EducationSessionRepository sessionRepository;
    private final UserRepository userRepository;

    public EducationSessionEventService(EducationSessionEventRepository eventRepository,
                                        EducationSessionRepository sessionRepository,
                                        UserRepository userRepository) {
        this.eventRepository = eventRepository;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void log(EducationSession session,
                    User actor,
                    EducationSessionEventType type,
                    String message) {
        EducationSessionEvent event = new EducationSessionEvent();
        event.setEducationSession(session);
        event.setActor(actor);
        event.setType(type);
        event.setMessage(message);

        eventRepository.save(event);
    }

    @Transactional
    public void log(String sessionCode,
                    String username,
                    EducationSessionEventType type,
                    String message) {
        EducationSession session = sessionRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> new IllegalArgumentException("Учебная сессия не найдена"));

        User actor = username == null
                ? null
                : userRepository.findByUsername(username).orElse(null);

        log(session, actor, type, message);
    }

    @Transactional(readOnly = true)
    public List<EducationSessionEvent> getEvents(String username, String sessionCode) {
        EducationSession session = sessionRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> new IllegalArgumentException("Учебная сессия не найдена"));

        boolean isTeacher = session.getTeacher() != null
                && session.getTeacher().getUsername().equals(username);

        if (!isTeacher) {
            throw new IllegalArgumentException("Журнал доступен только преподавателю");
        }

        return eventRepository.findByEducationSessionOrderByCreatedAtDesc(session);
    }

    @Transactional(readOnly = true)
    public List<EducationSessionEventResponse> getEventResponses(String username, String sessionCode) {
        return getEvents(username, sessionCode)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private EducationSessionEventResponse toResponse(EducationSessionEvent event) {
        return new EducationSessionEventResponse(
                event.getId(),
                event.getType(),
                event.getMessage(),
                event.getActor() != null ? event.getActor().getUsername() : null,
                event.getCreatedAt()
        );
    }
}