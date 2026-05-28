package com.remote.support.controller;

import com.remote.support.service.SupportSessionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/support/sessions")
public class SupportSessionController {

    private final SupportSessionService supportSessionService;

    public SupportSessionController(SupportSessionService supportSessionService) {
        this.supportSessionService = supportSessionService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> request) {
        String username = getCurrentUsername();

        String title = request.get("title") != null
                ? request.get("title").toString()
                : null;

        return ResponseEntity.ok(supportSessionService.create(username, title));
    }

    @GetMapping("/{sessionCode}")
    public ResponseEntity<Map<String, Object>> getByCode(@PathVariable String sessionCode) {
        return ResponseEntity.ok(supportSessionService.getByCode(sessionCode));
    }

    @PostMapping("/{sessionCode}/join")
    public ResponseEntity<Map<String, Object>> join(@PathVariable String sessionCode) {
        String username = getCurrentUsername();
        return ResponseEntity.ok(supportSessionService.join(username, sessionCode));
    }

    @PostMapping("/{sessionCode}/finish")
    public ResponseEntity<Map<String, Object>> finish(@PathVariable String sessionCode) {
        String username = getCurrentUsername();
        return ResponseEntity.ok(supportSessionService.finish(username, sessionCode));
    }

    @PostMapping("/{sessionCode}/control/request")
    public ResponseEntity<Map<String, Object>> requestControl(@PathVariable String sessionCode) {
        String username = getCurrentUsername();
        return ResponseEntity.ok(supportSessionService.requestControl(username, sessionCode));
    }

    @PostMapping("/{sessionCode}/control/allow")
    public ResponseEntity<Map<String, Object>> allowControl(@PathVariable String sessionCode) {
        String username = getCurrentUsername();
        return ResponseEntity.ok(supportSessionService.allowControl(username, sessionCode));
    }

    @PostMapping("/{sessionCode}/control/deny")
    public ResponseEntity<Map<String, Object>> denyControl(@PathVariable String sessionCode) {
        String username = getCurrentUsername();
        return ResponseEntity.ok(supportSessionService.denyControl(username, sessionCode));
    }

    @GetMapping("/my-active/operator")
    public ResponseEntity<?> getMyActiveOperatorSession() {
        String username = getCurrentUsername();

        Map<String, Object> response = supportSessionService.getMyActiveOperatorSession(username);

        if (response == null) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-active/client")
    public ResponseEntity<?> getMyActiveClientSession() {
        String username = getCurrentUsername();

        Map<String, Object> response = supportSessionService.getMyActiveClientSession(username);

        if (response == null) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(response);
    }

    private String getCurrentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}