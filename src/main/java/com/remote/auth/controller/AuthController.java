package com.remote.auth.controller;

import com.remote.auth.dto.AuthMessageResponse;
import com.remote.auth.dto.AuthRequest;
import com.remote.auth.dto.AuthTokenResponse;
import com.remote.auth.dto.ChangePasswordRequest;
import com.remote.auth.service.AuthService;
import com.remote.core.service.ClientIpService;
import com.remote.history.model.ConnectionLog;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final ClientIpService clientIpService;

    @PostMapping("/register")
    public AuthMessageResponse register(@Valid @RequestBody AuthRequest request,
                                        HttpServletRequest httpRequest) {
        return authService.register(request, clientIpService.getClientIp(httpRequest));
    }

    @PostMapping("/login")
    public AuthTokenResponse login(@Valid @RequestBody AuthRequest request,
                                   HttpServletRequest httpRequest) {
        return authService.login(request, clientIpService.getClientIp(httpRequest));
    }

    @PostMapping("/change-password")
    public AuthMessageResponse changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                              @RequestHeader("Authorization") String authHeader) {
        return authService.changePassword(authHeader, request);
    }

    @GetMapping("/logs")
    public List<ConnectionLog> getLogs(@RequestHeader("Authorization") String authHeader) {
        return authService.getLogs(authHeader);
    }
}