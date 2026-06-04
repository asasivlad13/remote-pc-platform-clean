package com.remote.frame.service;

import com.remote.core.model.User;
import com.remote.core.repository.UserRepository;
import com.remote.frame.dto.FrameUploadRequest;
import com.remote.pc.model.Pc;
import com.remote.pc.repository.PcRepository;
import com.remote.websocket.client.WebSocketClientHandler;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FrameService {

    private final PcRepository pcRepository;
    private final UserRepository userRepository;
    private final WebSocketClientHandler webSocketClientHandler;

    private final Map<String, String> lastFrames = new ConcurrentHashMap<>();

    public FrameService(PcRepository pcRepository,
                        UserRepository userRepository,
                        WebSocketClientHandler webSocketClientHandler) {
        this.pcRepository = pcRepository;
        this.userRepository = userRepository;
        this.webSocketClientHandler = webSocketClientHandler;
    }

    public void uploadFrame(FrameUploadRequest request) {
        String mac = request.mac();
        String imageBase64 = request.image();

        if (mac == null || mac.isBlank()) {
            throw new IllegalArgumentException("MAC address is missing");
        }

        if (imageBase64 == null || imageBase64.isBlank()) {
            throw new IllegalArgumentException("Frame image is missing");
        }

        Pc pc = pcRepository.findByMacAddress(mac);

        if (pc == null) {
            throw new IllegalArgumentException("PC not found");
        }

        lastFrames.put(mac, imageBase64);

        System.out.println("📸 Frame saved for " + mac + ", size: " + imageBase64.length() + " chars");

        webSocketClientHandler.broadcastFrame(pc.getId(), imageBase64);
    }

    public Map<String, String> getLatestFrame(String mac) {
        String imageBase64 = lastFrames.get(mac);

        if (imageBase64 == null) {
            throw new IllegalArgumentException("Frame not found");
        }

        return Map.of("image", imageBase64);
    }

    public Map<String, String> getMyPcsFrames(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        Map<String, String> result = new ConcurrentHashMap<>();

        for (Pc pc : pcRepository.findByUser(user)) {
            String frame = lastFrames.get(pc.getMacAddress());

            if (frame != null) {
                result.put(pc.getMacAddress(), frame);
            }
        }

        return result;
    }
}