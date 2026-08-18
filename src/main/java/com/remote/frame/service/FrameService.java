package com.remote.frame.service;

import com.remote.core.model.User;
import com.remote.core.repository.UserRepository;
import com.remote.frame.dto.FrameUploadRequest;
import com.remote.pc.model.Pc;
import com.remote.pc.repository.PcRepository;
import com.remote.websocket.client.WebSocketClientHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class FrameService {

    private final PcRepository pcRepository;
    private final UserRepository userRepository;
    private final WebSocketClientHandler webSocketClientHandler;

    /*
     * Кадры являются runtime-данными и не хранятся в БД.
     *
     * Ключом служит внутренний pcId, а не MAC.
     */
    private final Map<Long, String> lastFrames =
            new ConcurrentHashMap<>();

    public FrameService(
            PcRepository pcRepository,
            UserRepository userRepository,
            WebSocketClientHandler webSocketClientHandler
    ) {
        this.pcRepository = pcRepository;
        this.userRepository = userRepository;
        this.webSocketClientHandler = webSocketClientHandler;
    }

    public void uploadFrame(
            FrameUploadRequest request
    ) {
        Long pcId =
                request.pcId();

        String imageBase64 =
                request.image();

        if (imageBase64 == null
                || imageBase64.isBlank()) {

            throw new IllegalArgumentException(
                    "Frame image is missing"
            );
        }

        Pc pc =
                pcRepository.findById(pcId)
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "PC not found"
                                )
                        );

        lastFrames.put(
                pc.getId(),
                imageBase64
        );

        log.debug(
                "Frame saved: pcId={}, sizeChars={}",
                pc.getId(),
                imageBase64.length()
        );

        webSocketClientHandler.broadcastFrame(
                pc.getId(),
                imageBase64
        );
    }

    public Map<String, String> getLatestFrame(
            Long pcId
    ) {
        String imageBase64 =
                lastFrames.get(pcId);

        if (imageBase64 == null) {
            throw new IllegalArgumentException(
                    "Frame not found"
            );
        }

        return Map.of(
                "image",
                imageBase64
        );
    }

    public Map<Long, String> getMyPcsFrames(
            String email
    ) {
        /*
         * findByUsername пока является compatibility-методом
         * и фактически выполняет поиск по User.email.
         */
        User user =
                userRepository.findByUsername(email)
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Пользователь не найден"
                                )
                        );

        Map<Long, String> result =
                new ConcurrentHashMap<>();

        for (Pc pc : pcRepository.findByUser(user)) {
            String frame =
                    lastFrames.get(
                            pc.getId()
                    );

            if (frame != null) {
                result.put(
                        pc.getId(),
                        frame
                );
            }
        }

        return result;
    }
}