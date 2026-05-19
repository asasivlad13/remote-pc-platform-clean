package com.remote.service;

import com.remote.model.*;
import com.remote.repository.EducationSessionEventRepository;
import com.remote.repository.EducationSessionRepository;
import com.remote.repository.UserRepository;
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
}