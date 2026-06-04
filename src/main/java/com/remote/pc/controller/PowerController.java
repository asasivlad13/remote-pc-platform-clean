package com.remote.pc.controller;

import com.remote.core.service.CurrentUserService;
import com.remote.pc.service.PcPowerService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/pcs")
public class PowerController {

    private final PcPowerService pcPowerService;
    private final CurrentUserService currentUserService;

    @PostMapping("/{id}/sleep")
    public ResponseEntity<?> softSleepPc(@PathVariable Long id,
                                         HttpServletRequest request) {
        String username = currentUserService.extractUsername(request);
        return ResponseEntity.ok(pcPowerService.enableSoftSleep(id, username));
    }

    @PostMapping("/{id}/wake")
    public ResponseEntity<?> softWakePc(@PathVariable Long id,
                                        HttpServletRequest request) {
        String username = currentUserService.extractUsername(request);
        return ResponseEntity.ok(pcPowerService.disableSoftSleep(id, username));
    }
}