package com.remote.history.service;

import com.remote.core.model.User;
import com.remote.core.repository.UserRepository;
import com.remote.history.dto.HistoryResponse;
import com.remote.remoteaccess.repository.RemoteSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class HistoryService {

    private final RemoteSessionRepository
            remoteSessionRepository;

    private final UserRepository
            userRepository;

    public HistoryService(
            RemoteSessionRepository remoteSessionRepository,
            UserRepository userRepository
    ) {
        this.remoteSessionRepository =
                remoteSessionRepository;

        this.userRepository =
                userRepository;
    }

    @Transactional(readOnly = true)
    public List<HistoryResponse> getHistory(
            String email
    ) {
        User user =
                userRepository
                        .findByEmail(
                                email
                        )
                        .orElse(null);

        if (user == null) {
            return List.of();
        }

        return remoteSessionRepository
                .findByUserIdOrderByStartedAtDesc(
                        user.getId()
                )
                .stream()
                .map(
                        remoteSession ->
                                new HistoryResponse(
                                        remoteSession.getId(),
                                        remoteSession.getSessionId(),
                                        resolveUserEmail(
                                                remoteSession
                                        ),
                                        remoteSession.getPcName(),
                                        remoteSession.getClientIp(),
                                        remoteSession.getClientInfo(),
                                        remoteSession.getMode(),
                                        remoteSession.getProfile(),
                                        remoteSession.getStartedAt(),
                                        remoteSession.getEndedAt(),
                                        remoteSession.getDurationSeconds(),
                                        remoteSession.getAvgFps(),
                                        remoteSession.getAvgLatency(),
                                        remoteSession.getFilesSent(),
                                        remoteSession.getIssues()
                                )
                )
                .toList();
    }

    private String resolveUserEmail(
            com.remote.remoteaccess.model.RemoteSession
                    remoteSession
    ) {
        if (remoteSession.getUserEmail()
                != null
                && !remoteSession.getUserEmail()
                .isBlank()) {

            return remoteSession
                    .getUserEmail();
        }

        if (remoteSession.getUser()
                != null) {

            return remoteSession
                    .getUser()
                    .getEmail();
        }

        return "unknown";
    }
}