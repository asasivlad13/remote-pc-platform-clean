package com.remote.core.service;

import com.remote.education.model.EducationSessionStatus;
import com.remote.education.repository.EducationSessionRepository;
import com.remote.support.model.SupportSessionStatus;
import com.remote.support.repository.SupportSessionRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StartupSessionCleanupService {

    private final EducationSessionRepository educationSessionRepository;
    private final SupportSessionRepository supportSessionRepository;

    public StartupSessionCleanupService(EducationSessionRepository educationSessionRepository,
                                        SupportSessionRepository supportSessionRepository) {
        this.educationSessionRepository = educationSessionRepository;
        this.supportSessionRepository = supportSessionRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void closeUnfinishedSessionsAfterRestart() {
        educationSessionRepository.findAll()
                .stream()
                .filter(session -> session.getStatus() == EducationSessionStatus.ACTIVE)
                .forEach(session -> {
                    session.finish();
                    educationSessionRepository.save(session);
                    System.out.println("Startup cleanup: education session closed: " + session.getSessionCode());
                });

        supportSessionRepository.findAll()
                .stream()
                .filter(session ->
                        session.getStatus() == SupportSessionStatus.ACTIVE
                                || session.getStatus() == SupportSessionStatus.WAITING_CLIENT
                )
                .forEach(session -> {
                    if (session.getStatus() == SupportSessionStatus.ACTIVE) {
                        session.finish();
                    } else {
                        session.cancel();
                    }

                    supportSessionRepository.save(session);
                    System.out.println("Startup cleanup: support session closed: " + session.getSessionCode());
                });
    }
}