package com.tts.sms.controller;

import com.tts.sms.service.StudentCategoryService;
import com.tts.sms.service.SystemConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/system-config")
@RequiredArgsConstructor
public class SystemConfigurationController {

    private final SystemConfigurationService configService;
    private final StudentCategoryService categoryService;

    @GetMapping("/cutoff-date")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, String>> getCutoffDate() {
        LocalDate cutoffDate = configService.getCutoffDate();
        return ResponseEntity.ok(Map.of(
                "cutoffDate", cutoffDate.toString(),
                "message", "Current cutoff date"
        ));
    }

    @PostMapping("/cutoff-date")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, String>> setCutoffDate(
            @RequestBody Map<String, String> request,
            Authentication authentication) {

        String dateStr = request.get("cutoffDate");
        LocalDate cutoffDate = LocalDate.parse(dateStr);
        String username = authentication.getName();

        log.info("📅 SUPER_ADMIN {} changing cutoff date to: {}", username, cutoffDate);

        configService.setCutoffDate(cutoffDate, username);

        log.info("🔄 Triggering mass recategorization...");
        categoryService.recategorizeAllStudents();

        return ResponseEntity.ok(Map.of(
                "success", "true",
                "message", "Cutoff date updated and students recategorized",
                "cutoffDate", cutoffDate.toString()
        ));
    }
}