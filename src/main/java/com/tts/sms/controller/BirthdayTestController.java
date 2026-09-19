package com.tts.sms.controller;

/*
 * Temporarily disabled Birthday wishes / test controller.
 *
 * import com.tts.sms.scheduler.FeeDueReminderScheduler;
 * import lombok.RequiredArgsConstructor;
 * import org.springframework.format.annotation.DateTimeFormat;
 * import org.springframework.http.ResponseEntity;
 * import org.springframework.jdbc.core.JdbcTemplate;
 * import org.springframework.web.bind.annotation.GetMapping;
 * import org.springframework.web.bind.annotation.RequestMapping;
 * import org.springframework.web.bind.annotation.RequestParam;
 * import org.springframework.web.bind.annotation.RestController;
 * 
 * import java.time.LocalDate;
 * import java.util.HashMap;
 * import java.util.Map;
 * 
 * @RestController
 * 
 * @RequestMapping("/api/test")
 * 
 * @RequiredArgsConstructor
 * public class BirthdayTestController {
 * 
 * private final FeeDueReminderScheduler feeDueReminderScheduler;
 * private final JdbcTemplate jdbcTemplate;
 * 
 * @GetMapping("/send-fee-reminders")
 * public ResponseEntity<Map<String, Object>> sendFeeDueRemindersManually(
 * 
 * @RequestParam(required = false) @DateTimeFormat(iso =
 * DateTimeFormat.ISO.DATE) LocalDate targetDate) {
 * 
 * if (targetDate != null) {
 * Map<String, Object> result =
 * feeDueReminderScheduler.sendFeeRemindersForDate(targetDate);
 * return ResponseEntity.ok(result);
 * } else {
 * Map<String, Object> result =
 * feeDueReminderScheduler.sendDailyCountdownFeeDueReminders(LocalDate.now());
 * return ResponseEntity.ok(result);
 * }
 * }
 * 
 * @GetMapping("/set-all-students-test-email")
 * public ResponseEntity<Map<String, Object>> setAllStudentsTestEmail(
 * 
 * @RequestParam(defaultValue = "ktm.lover0123@gmail.com") String email) {
 * jdbcTemplate.
 * execute("CREATE TABLE IF NOT EXISTS temp_admissions_email_backup AS SELECT id, registration_number, email_primary, email_secondary FROM admissions;"
 * );
 * int updated = jdbcTemplate.update("UPDATE admissions SET email_primary = ?",
 * email);
 * return ResponseEntity.ok(Map.of(
 * "status", "SUCCESS",
 * "message", "All student emails updated for local testing",
 * "email", email,
 * "updatedStudents", updated
 * ));
 * }
 * 
 * @GetMapping("/restore-original-emails")
 * public ResponseEntity<Map<String, Object>> restoreOriginalEmails() {
 * int restored = jdbcTemplate.
 * update("UPDATE admissions a JOIN temp_admissions_email_backup b ON a.id = b.id SET a.email_primary = b.email_primary, a.email_secondary = b.email_secondary;"
 * );
 * return ResponseEntity.ok(Map.of(
 * "status", "SUCCESS",
 * "message", "Student emails restored from backup table",
 * "restoredStudents", restored
 * ));
 * }
 * }
 */
