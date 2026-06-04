package com.remote.history.service;

import com.remote.history.dto.HistoryResponse;
import com.remote.history.repository.ConnectionLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class HistoryService {

    private final ConnectionLogRepository connectionLogRepository;

    public HistoryService(ConnectionLogRepository connectionLogRepository) {
        this.connectionLogRepository = connectionLogRepository;
    }

    @Transactional(readOnly = true)
    public List<HistoryResponse> getHistory(String username) {
        return connectionLogRepository.findByUsernameOrderByTimestampDesc(username)
                .stream()
                .map(log -> new HistoryResponse(
                        log.getId(),
                        log.getSessionId(),
                        log.getUsername(),
                        log.getPcName(),
                        log.getClientIp(),
                        log.getClientInfo(),
                        log.getMode(),
                        log.getTimestamp(),
                        log.getDisconnectedAt(),
                        log.getDurationSeconds(),
                        log.getAvgFps(),
                        log.getAvgLatency(),
                        log.getFilesSent(),
                        log.getIssues()
                ))
                .toList();
    }
}