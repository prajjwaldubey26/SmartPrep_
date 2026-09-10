package com.smartprep.controller;

import com.smartprep.service.DashboardService;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return dashboardService.summary(userId);
    }

    @GetMapping("/sessions")
    public Map<String, Object> sessions(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return dashboardService.sessions(userId);
    }
}
