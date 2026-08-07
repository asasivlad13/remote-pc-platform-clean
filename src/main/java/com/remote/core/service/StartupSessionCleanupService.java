package com.remote.core.service;

import com.remote.education.model.EducationSessionStatus;
import com.remote.education.repository.EducationSessionRepository;
import com.remote.support.model.SupportSessionStatus;
import com.remote.support.repository.SupportSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
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

                    log.info(
                            "Startup cleanup: education session closed: sessionCode={}",
                            session.getSessionCode()
                    );
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

                    log.info(
                            "Startup cleanup: support session closed: sessionCode={}",
                            session.getSessionCode()
                    );
                });
    }
}