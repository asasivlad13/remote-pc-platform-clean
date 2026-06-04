package com.remote.support.controller;

import com.remote.core.service.CurrentUserService;
import com.remote.support.dto.SupportSessionCreateRequest;
import com.remote.support.dto.SupportSessionResponse;
import com.remote.support.service.SupportSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/support/sessions")
public class SupportSessionController {

    private final SupportSessionService supportSessionService;
    private final CurrentUserService currentUserService;

    @PostMapping
    public SupportSessionResponse create(@Valid @RequestBody SupportSessionCreateRequest request,
                                         HttpServletRequest httpRequest) {
        String username = currentUserService.extractUsername(httpRequest);
        return supportSessionService.create(username, request.title());
    }

    @GetMapping("/{sessionCode}")
    public SupportSessionResponse getByCode(@PathVariable String sessionCode) {
        return supportSessionService.getByCode(sessionCode);
    }

    @PostMapping("/{sessionCode}/join")
    public SupportSessionResponse join(@PathVariable String sessionCode,
                                       HttpServletRequest httpRequest) {
        String username = currentUserService.extractUsername(httpRequest);
        return supportSessionService.join(username, sessionCode);
    }

    @PostMapping("/{sessionCode}/finish")
    public SupportSessionResponse finish(@PathVariable String sessionCode,
                                         HttpServletRequest httpRequest) {
        String username = currentUserService.extractUsername(httpRequest);
        return supportSessionService.finish(username, sessionCode);
    }

    @PostMapping("/{sessionCode}/control/request")
    public SupportSessionResponse requestControl(@PathVariable String sessionCode,
                                                 HttpServletRequest httpRequest) {
        String username = currentUserService.extractUsername(httpRequest);
        return supportSessionService.requestControl(username, sessionCode);
    }

    @PostMapping("/{sessionCode}/control/allow")
    public SupportSessionResponse allowControl(@PathVariable String sessionCode,
                                               HttpServletRequest httpRequest) {
        String username = currentUserService.extractUsername(httpRequest);
        return supportSessionService.allowControl(username, sessionCode);
    }

    @PostMapping("/{sessionCode}/control/deny")
    public SupportSessionResponse denyControl(@PathVariable String sessionCode,
                                              HttpServletRequest httpRequest) {
        String username = currentUserService.extractUsername(httpRequest);
        return supportSessionService.denyControl(username, sessionCode);
    }

    @GetMapping("/my-active/operator")
    public ResponseEntity<SupportSessionResponse> getMyActiveOperatorSession(HttpServletRequest httpRequest) {
        String username = currentUserService.extractUsername(httpRequest);

        SupportSessionResponse response = supportSessionService.getMyActiveOperatorSession(username);

        if (response == null) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-active/client")
    public ResponseEntity<SupportSessionResponse> getMyActiveClientSession(HttpServletRequest httpRequest) {
        String username = currentUserService.extractUsername(httpRequest);

        SupportSessionResponse response = supportSessionService.getMyActiveClientSession(username);

        if (response == null) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(response);
    }
}