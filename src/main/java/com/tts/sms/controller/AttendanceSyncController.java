package com.tts.sms.controller;

import com.tts.sms.dto.AttendanceSyncResponseDTO;
import com.tts.sms.service.AttendanceSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller for managing and triggering student data sync to external
 * attendance server (attendance.ttsnasik.com)
 */
@RestController
@RequestMapping("/api/attendance-sync")
@RequiredArgsConstructor
@Slf4j
public class AttendanceSyncController {

    private final AttendanceSyncService attendanceSyncService;

    @Value("${app.attendance-sync.enabled:true}")
    private boolean attendanceSyncEnabled;

    @Value("${app.attendance-sync.server-url:https://attendance.ttsnasik.com}")
    private String serverUrl;

    /**
     * Pushes a single student record to the third-party attendance server by
     * Registration Number
     * POST /api/attendance-sync/push-by-regno/{regNo}
     */
    @PostMapping("/push-by-regno/{regNo}")
    public ResponseEntity<AttendanceSyncResponseDTO> pushStudentByRegNo(@PathVariable("regNo") String regNo) {
        log.info("Received request to push student [{}] to attendance server", regNo);
        try {
            AttendanceSyncResponseDTO result = attendanceSyncService.syncStudentByRegNo(regNo);
            if (result.isSuccess()) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
            }
        } catch (IllegalArgumentException e) {
            log.warn("Invalid regNo parameter: {}", e.getMessage());
            return ResponseEntity.badRequest().body(AttendanceSyncResponseDTO.builder()
                    .success(false)
                    .message(e.getMessage())
                    .processedCount(0)
                    .build());
        } catch (Exception e) {
            log.error("Failed to push student [{}] to attendance server: {}", regNo, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(AttendanceSyncResponseDTO.builder()
                    .success(false)
                    .message("Internal server error: " + e.getMessage())
                    .processedCount(0)
                    .build());
        }
    }

    /**
     * Pushes all students admitted within a date range to the third-party
     * attendance server
     * POST
     * /api/attendance-sync/push-by-date-range?fromDate=2026-01-01&toDate=2026-07-20
     */
    @PostMapping("/push-by-date-range")
    public ResponseEntity<AttendanceSyncResponseDTO> pushStudentsByDateRange(
            @RequestParam("fromDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam("toDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {

        log.info("Received request to push student records from {} to {} to attendance server", fromDate, toDate);
        try {
            AttendanceSyncResponseDTO result = attendanceSyncService.syncStudentsByDateRange(fromDate, toDate);
            if (result.isSuccess()) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
            }
        } catch (IllegalArgumentException e) {
            log.warn("Invalid date parameters: {}", e.getMessage());
            return ResponseEntity.badRequest().body(AttendanceSyncResponseDTO.builder()
                    .success(false)
                    .message(e.getMessage())
                    .processedCount(0)
                    .build());
        } catch (Exception e) {
            log.error("Failed to push date range records to attendance server: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(AttendanceSyncResponseDTO.builder()
                    .success(false)
                    .message("Internal server error: " + e.getMessage())
                    .processedCount(0)
                    .build());
        }
    }

    /**
     * Check status and configuration of Attendance Sync integration
     * GET /api/attendance-sync/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSyncStatus() {
        Map<String, Object> statusMap = new HashMap<>();
        statusMap.put("enabled", attendanceSyncEnabled);
        statusMap.put("targetServerUrl", serverUrl);
        statusMap.put("status", attendanceSyncEnabled ? "ACTIVE" : "DISABLED");
        return ResponseEntity.ok(statusMap);
    }
}
