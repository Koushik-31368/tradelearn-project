package com.tradelearn.server.analytics.controller;

import com.tradelearn.server.analytics.service.AnalyticsService;
import com.tradelearn.server.analytics.service.ReadinessScoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private ReadinessScoreService readinessScoreService;

    @GetMapping("/user/{userId}")
    public ResponseEntity<Map<String, Object>> getUserAnalytics(@PathVariable Long userId) {
        Map<String, Object> analytics = analyticsService.getUserAnalytics(userId);
        return ResponseEntity.ok(analytics);
    }

    @GetMapping("/user/{userId}/readiness")
    public ResponseEntity<Map<String, Object>> getUserReadiness(@PathVariable Long userId) {
        Map<String, Object> readiness = readinessScoreService.evaluateReadiness(userId);
        return ResponseEntity.ok(readiness);
    }
}
