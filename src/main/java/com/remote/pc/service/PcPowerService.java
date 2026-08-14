package com.remote.pc.service;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.remote.core.model.User;
import com.remote.core.repository.UserRepository;
import com.remote.pc.model.Pc;
import com.remote.pc.model.PcStatus;
import com.remote.pc.repository.PcRepository;
import com.remote.websocket.agent.AgentWebSocketHandler;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Map;

import static com.remote.common.ServerConstants.ACTION_SOFT_SLEEP;
import static com.remote.common.ServerConstants.ACTION_SOFT_WAKE;

@Service
public class PcPowerService {

    private final PcRepository pcRepository;
    private final UserRepository userRepository;
    private final AgentWebSocketHandler agentWebSocketHandler;
    private final ObjectMapper objectMapper;

    public PcPowerService(PcRepository pcRepository,
                          UserRepository userRepository,
                          @Lazy AgentWebSocketHandler agentWebSocketHandler,
                          ObjectMapper objectMapper) {
        this.pcRepository = pcRepository;
        this.userRepository = userRepository;
        this.agentWebSocketHandler = agentWebSocketHandler;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, String> enableSoftSleep(Long pcId, String username) {
        Pc pc = findUserPc(pcId, username);

        sendPowerCommand(pc, ACTION_SOFT_SLEEP);

        pc.setStatus(PcStatus.SLEEP);
        pcRepository.save(pc);

        return Map.of("message", "Режим ожидания включён");
    }

    @Transactional
    public Map<String, String> disableSoftSleep(Long pcId, String username) {
        Pc pc = findUserPc(pcId, username);

        sendPowerCommand(pc, ACTION_SOFT_WAKE);

        pc.setStatus(PcStatus.ONLINE);
        pcRepository.save(pc);

        return Map.of("message", "ПК выведен из режима ожидания");
    }

    private Pc findUserPc(Long pcId, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Пользователь не найден"
                ));

        Pc pc = pcRepository.findById(pcId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "ПК не найден"
                ));

        if (pc.getUser() == null
                || !pc.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException(
                    "Доступ запрещён"
            );
        }

        return pc;
    }

    private void sendPowerCommand(Pc pc, String action) {
        try {
            ObjectNode command = objectMapper.createObjectNode();

            command.put("type", "command");
            command.put("pcId", pc.getId());
            command.put("action", action);

            agentWebSocketHandler.sendCommandToAgent(
                    pc.getId(),
                    command
            );

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Не удалось отправить команду агенту",
                    e
            );
        }
    }
}