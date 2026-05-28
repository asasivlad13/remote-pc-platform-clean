package com.remote.pc.service;

import com.remote.pc.model.Pc;
import com.remote.pc.model.PcStatus;
import com.remote.pc.repository.PcRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PcOnlineStatusMonitorService {

    private static final int OFFLINE_TIMEOUT_SECONDS = 30;

    private final PcRepository pcRepository;

    public PcOnlineStatusMonitorService(PcRepository pcRepository) {
        this.pcRepository = pcRepository;
    }

    @Scheduled(fixedDelay = 10000)
    public void markInactivePcsOffline() {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(OFFLINE_TIMEOUT_SECONDS);

        List<Pc> pcs = pcRepository.findAll();

        for (Pc pc : pcs) {
            if (pc.getStatus() == PcStatus.ONLINE
                    && pc.getLastConnection() != null
                    && pc.getLastConnection().isBefore(threshold)) {

                pc.setStatus(PcStatus.OFFLINE);
                pcRepository.save(pc);

                System.out.println("PC " + pc.getName() + " marked OFFLINE by heartbeat timeout");
            }
        }
    }
}