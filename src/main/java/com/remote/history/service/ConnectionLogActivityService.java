package com.remote.history.service;

import com.remote.file.service.FileStatisticsService;
import com.remote.history.model.ConnectionLog;
import com.remote.history.repository.ConnectionLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConnectionLogActivityService {

    private final ConnectionLogRepository connectionLogRepository;
    private final FileStatisticsService fileStatisticsService;

    public ConnectionLogActivityService(ConnectionLogRepository connectionLogRepository,
                                        FileStatisticsService fileStatisticsService) {
        this.connectionLogRepository = connectionLogRepository;
        this.fileStatisticsService = fileStatisticsService;
    }

    @Transactional
    public void incrementFilesSent(String username, String pcName) {
        ConnectionLog log = findActiveLog(username, pcName);
        fileStatisticsService.incrementFilesSent(log);
    }

    private ConnectionLog findActiveLog(String username, String pcName) {
        ConnectionLog log = null;

        if (pcName != null && !pcName.isBlank()) {
            log = connectionLogRepository
                    .findFirstByUsernameAndPcNameAndDisconnectedAtIsNullOrderByTimestampDesc(username, pcName)
                    .orElse(null);
        }

        if (log == null) {
            log = connectionLogRepository
                    .findFirstByUsernameAndDisconnectedAtIsNullOrderByTimestampDesc(username)
                    .orElse(null);
        }

        return log;
    }
}