package com.remote.pc.service;

import com.remote.core.model.User;
import com.remote.core.repository.UserRepository;
import com.remote.pc.dto.PcDetailsResponse;
import com.remote.pc.dto.PcResponseDto;
import com.remote.pc.model.Pc;
import com.remote.pc.repository.PcRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PcService {

    private final PcRepository pcRepository;
    private final UserRepository userRepository;

    public PcService(PcRepository pcRepository,
                     UserRepository userRepository) {
        this.pcRepository = pcRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<PcResponseDto> getMyPcs(String username) {
        User user = findUser(username);

        return pcRepository.findByUser(user)
                .stream()
                .map(this::toPcResponseDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public PcDetailsResponse getMyPcById(Long pcId, String username) {
        User user = findUser(username);

        Pc pc = pcRepository.findById(pcId)
                .orElseThrow(() -> new IllegalArgumentException("ПК не найден"));

        if (pc.getUser() == null || !pc.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Доступ запрещён");
        }

        return toDetailsResponse(pc);
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
    }

    private PcResponseDto toPcResponseDto(Pc pc) {
        return new PcResponseDto(
                pc.getId(),
                pc.getName(),
                pc.getMacAddress(),
                pc.getStatus(),
                pc.getLastConnection()
        );
    }

    private PcDetailsResponse toDetailsResponse(Pc pc) {
        return new PcDetailsResponse(
                pc.getId(),
                pc.getName(),
                pc.getMacAddress(),
                pc.getStatus(),
                pc.getLastConnection(),
                pc.getScreenWidth() != null ? pc.getScreenWidth() : 1920,
                pc.getScreenHeight() != null ? pc.getScreenHeight() : 1080,
                pc.getWebrtcUrl(),
                pc.getStreamName()
        );
    }
}