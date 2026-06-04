package com.remote.pc.controller;

import com.remote.core.service.CurrentUserService;
import com.remote.pc.dto.PcDetailsResponse;
import com.remote.pc.dto.PcResponseDto;
import com.remote.pc.service.PcService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/pcs")
public class PcController {

    private final PcService pcService;
    private final CurrentUserService currentUserService;

    @GetMapping
    public List<PcResponseDto> getMyPcs(HttpServletRequest request) {
        String username = currentUserService.extractUsername(request);
        return pcService.getMyPcs(username);
    }

    @GetMapping("/{id}")
    public PcDetailsResponse getPcById(@PathVariable Long id,
                                       HttpServletRequest request) {
        String username = currentUserService.extractUsername(request);
        return pcService.getMyPcById(id, username);
    }
}