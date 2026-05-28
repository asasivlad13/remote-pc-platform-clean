package com.remote.websocket.client.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LastFrameCache {

    private final Map<Long, String> lastFrames = new ConcurrentHashMap<>();

    public void put(Long pcId, String frameBase64) {
        lastFrames.put(pcId, frameBase64);
    }

    public String get(Long pcId) {
        return lastFrames.get(pcId);
    }

    public void remove(Long pcId) {
        lastFrames.remove(pcId);
    }
}