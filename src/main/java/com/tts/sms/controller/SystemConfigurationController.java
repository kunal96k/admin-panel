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
    private final com.tts.sms.scheduler.FeeDueReminderScheduler feeDueReminderScheduler;

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

        log.info("[DATE] SUPER_ADMIN {} changing cutoff date to: {}", username, cutoffDate);

        configService.setCutoffDate(cutoffDate, username);

        log.info("[SYNC] Triggering mass recategorization...");
        categoryService.recategorizeAllStudents();

        return ResponseEntity.ok(Map.of(
                "success", "true",
                "message", "Cutoff date updated and students recategorized",
                "cutoffDate", cutoffDate.toString()
        ));
    }

    @GetMapping("/fee-reminder-status")
    public ResponseEntity<Map<String, Object>> getFeeReminderStatus() {
        boolean emailEnabled = configService.isFeeReminderEmailEnabled();
        boolean whatsappEnabled = configService.isFeeReminderWhatsAppEnabled();
        return ResponseEntity.ok(Map.of(
                "enabled", emailEnabled,
                "emailEnabled", emailEnabled,
                "whatsappEnabled", whatsappEnabled,
                "message", String.format("Fee reminders: Email is %s, WhatsApp is %s",
                        emailEnabled ? "ENABLED" : "DISABLED",
                        whatsappEnabled ? "ENABLED" : "DISABLED")
        ));
    }

    @GetMapping("/whatsapp-features-status")
    public ResponseEntity<Map<String, Object>> getWhatsAppFeaturesStatus(Authentication authentication) {
        boolean isSuperAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_SUPER_ADMIN".equalsIgnoreCase(a.getAuthority()) || "SUPER_ADMIN".equalsIgnoreCase(a.getAuthority()));

        boolean courseInfoEnabled = configService.isCourseInfoWhatsAppEnabled();
        boolean feeReminderEnabled = configService.isFeeReminderWhatsAppEnabled();
        boolean emailReminderEnabled = configService.isFeeReminderEmailEnabled();

        return ResponseEntity.ok(Map.of(
                "isSuperAdmin", isSuperAdmin,
                "courseInfoWhatsAppEnabled", courseInfoEnabled,
                "feeReminderWhatsAppEnabled", feeReminderEnabled,
                "feeReminderEmailEnabled", emailReminderEnabled
        ));
    }

    @PostMapping("/course-info-whatsapp-status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> setCourseInfoWhatsAppStatus(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {

        boolean enabled = Boolean.parseBoolean(String.valueOf(request.get("enabled")));
        String username = authentication != null ? authentication.getName() : "system";

        log.info("[NOTIF] SUPER_ADMIN {} toggling course info WhatsApp enabled to: {}", username, enabled);
        configService.setCourseInfoWhatsAppEnabled(enabled, username);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "enabled", enabled,
                "message", enabled ? "WhatsApp Course Information API enabled" : "WhatsApp Course Information API disabled"
        ));
    }

    @PostMapping("/fee-reminder-status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> setFeeReminderStatus(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {

        String username = authentication != null ? authentication.getName() : "system";

        if (request.containsKey("enabled")) {
            boolean enabled = Boolean.parseBoolean(String.valueOf(request.get("enabled")));
            log.info("[NOTIF] SUPER_ADMIN {} toggling fee reminder email enabled to: {}", username, enabled);
            configService.setFeeReminderEmailEnabled(enabled, username);
        }

        if (request.containsKey("whatsappEnabled")) {
            boolean waEnabled = Boolean.parseBoolean(String.valueOf(request.get("whatsappEnabled")));
            log.info("[NOTIF] SUPER_ADMIN {} toggling fee reminder WhatsApp enabled to: {}", username, waEnabled);
            configService.setFeeReminderWhatsAppEnabled(waEnabled, username);
        }

        boolean emailEnabled = configService.isFeeReminderEmailEnabled();
        boolean whatsappEnabled = configService.isFeeReminderWhatsAppEnabled();

        return ResponseEntity.ok(Map.of(
                "success", true,
                "enabled", emailEnabled,
                "emailEnabled", emailEnabled,
                "whatsappEnabled", whatsappEnabled,
                "message", "Fee reminder status updated successfully"
        ));
    }

    @GetMapping("/fee-reminder-whatsapp-status")
    public ResponseEntity<Map<String, Object>> getFeeReminderWhatsAppStatus() {
        boolean enabled = configService.isFeeReminderWhatsAppEnabled();
        return ResponseEntity.ok(Map.of(
                "enabled", enabled,
                "message", enabled ? "Fee reminder WhatsApp messages are ENABLED" : "Fee reminder WhatsApp messages are DISABLED"
        ));
    }

    @PostMapping("/fee-reminder-whatsapp-status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> setFeeReminderWhatsAppStatus(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {

        boolean enabled = Boolean.parseBoolean(String.valueOf(request.get("enabled")));
        String username = authentication != null ? authentication.getName() : "system";

        log.info("[NOTIF] SUPER_ADMIN {} toggling fee reminder WhatsApp enabled to: {}", username, enabled);
        configService.setFeeReminderWhatsAppEnabled(enabled, username);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "enabled", enabled,
                "message", enabled ? "Fee reminder WhatsApp messages enabled" : "Fee reminder WhatsApp messages disabled"
        ));
    }

    /**
     * Manually triggers a full student recategorization NOW.
     * Fixes all stale NEW_STUDENT badges in the DB immediately.
     * Useful after deploying the 7-day window logic for the first time.
     */
    @PostMapping("/recategorize-now")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> triggerRecategorizeNow(Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "system";
        log.info("[MANUAL TRIGGER] SUPER_ADMIN {} manually triggering student recategorization", username);

        long startMs = System.currentTimeMillis();
        categoryService.recategorizeAllStudents();
        long elapsedMs = System.currentTimeMillis() - startMs;

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Student recategorization completed. All stale NEW_STUDENT badges are now fixed.",
                "triggeredBy", username,
                "elapsedMs", elapsedMs
        ));
    }

    /**
     * Manually triggers the 5-day fee due countdown reminders (Email and/or WhatsApp) on demand.
     */
    @PostMapping("/fee-reminder/trigger-now")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> triggerFeeRemindersNow() {
        log.info("[MANUAL TRIGGER] SUPER_ADMIN manually triggering 5-day fee countdown reminders");
        Map<String, Object> result = feeDueReminderScheduler.sendDailyCountdownFeeDueReminders(LocalDate.now());
        return ResponseEntity.ok(result);
    }
}