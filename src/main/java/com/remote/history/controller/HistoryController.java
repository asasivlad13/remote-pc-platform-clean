package com.remote.history.controller;

import com.remote.core.service.CurrentUserService;
import com.remote.history.dto.HistoryResponse;
import com.remote.history.service.HistoryService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/history")
public class HistoryController {

    private final HistoryService historyService;
    private final CurrentUserService currentUserService;

    @GetMapping
    public List<HistoryResponse> getHistory(HttpServletRequest request) {
        String username = currentUserService.extractUsername(request);
        return historyService.getHistory(username);
    }
}