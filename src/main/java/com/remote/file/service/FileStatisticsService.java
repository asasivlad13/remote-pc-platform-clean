package com.remote.file.service;

import com.remote.history.model.ConnectionLog;
import com.remote.history.repository.ConnectionLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FileStatisticsService {

    private final ConnectionLogRepository connectionLogRepository;

    public FileStatisticsService(ConnectionLogRepository connectionLogRepository) {
        this.connectionLogRepository = connectionLogRepository;
    }

    @Transactional
    public void incrementFilesSent(ConnectionLog log) {
        if (log == null) {
            return;
        }

        int currentFilesSent = log.getFilesSent() != null
                ? log.getFilesSent()
                : 0;

        log.setFilesSent(currentFilesSent + 1);

        connectionLogRepository.save(log);
    }
}