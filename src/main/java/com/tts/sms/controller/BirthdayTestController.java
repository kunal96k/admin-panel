package com.tts.sms.controller;

import com.tts.sms.scheduler.FeeDueReminderScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Test controller for manually triggering scheduled emails (Fee Due Reminders)
 * and managing local test email configurations.
 */
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class BirthdayTestController {

    private final FeeDueReminderScheduler feeDueReminderScheduler;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Manually trigger fee due reminder email sending
     * GET /api/test/send-fee-reminders
     * GET /api/test/send-fee-reminders?targetDate=2026-09-16
     *
     * Defaults to today + 5 days if targetDate is omitted.
     */
    @GetMapping("/send-fee-reminders")
    public ResponseEntity<Map<String, Object>> sendFeeDueRemindersManually(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate) {

        LocalDate effectiveDate = (targetDate != null) ? targetDate : LocalDate.now().plusDays(5);
        Map<String, Object> result = feeDueReminderScheduler.sendFeeRemindersForDate(effectiveDate);
        return ResponseEntity.ok(result);
    }

    /**
     * Helper endpoint for local testing: update all students' email to a test email address
     * GET /api/test/set-all-students-test-email
     * GET /api/test/set-all-students-test-email?email=ktm.lover0123@gmail.com
     */
    @GetMapping("/set-all-students-test-email")
    public ResponseEntity<Map<String, Object>> setAllStudentsTestEmail(
            @RequestParam(defaultValue = "ktm.lover0123@gmail.com") String email) {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS temp_admissions_email_backup AS SELECT id, registration_number, email_primary, email_secondary FROM admissions;");
        int updated = jdbcTemplate.update("UPDATE admissions SET email_primary = ?", email);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "All student emails updated for local testing",
                "email", email,
                "updatedStudents", updated
        ));
    }

    /**
     * Helper endpoint for local testing: restore original student emails from backup table
     * GET /api/test/restore-original-emails
     */
    @GetMapping("/restore-original-emails")
    public ResponseEntity<Map<String, Object>> restoreOriginalEmails() {
        int restored = jdbcTemplate.update("UPDATE admissions a JOIN temp_admissions_email_backup b ON a.id = b.id SET a.email_primary = b.email_primary, a.email_secondary = b.email_secondary;");
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Student emails restored from backup table",
                "restoredStudents", restored
        ));
    }
}