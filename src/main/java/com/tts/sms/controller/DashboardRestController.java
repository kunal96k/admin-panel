package com.tts.sms.controller;

import com.tts.sms.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class DashboardRestController {

    private final DashboardService dashboardService;

    /**
     * Get dashboard statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getDashboardStats() {
        log.info("GET /api/dashboard/stats");
        Map<String, Object> stats = dashboardService.getDashboardStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * Get revenue chart data by period
     */
    @GetMapping("/revenue-chart")
    public ResponseEntity<Map<String, Object>> getRevenueChart(
            @RequestParam(defaultValue = "6M") String period) {
        log.info("GET /api/dashboard/revenue-chart - period: {}", period);
        Map<String, Object> chartData = dashboardService.getRevenueChartData(period);
        return ResponseEntity.ok(chartData);
    }

    /**
     * Get course distribution chart data
     */
    @GetMapping("/course-distribution")
    public ResponseEntity<Map<String, Object>> getCourseDistribution() {
        log.info("GET /api/dashboard/course-distribution");
        Map<String, Object> chartData = dashboardService.getCourseDistributionData();
        return ResponseEntity.ok(chartData);
    }
}