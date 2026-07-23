package com.tts.sms.controller;

import com.tts.sms.service.BackupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Controller to trigger database and file backups manually
 */
@RestController
@RequestMapping("/api/auth/backup")
@RequiredArgsConstructor
@Slf4j
public class BackupController {

    private final BackupService backupService;
    private final Executor emailTaskExecutor;

    @Value("${app.backup.email}")
    private String backupEmail;

    @Value("${app.backup.passcode}")
    private String backupPasscode;

    /**
     * Manually triggers backup process after validating email and passcode.
     * POST /api/auth/backup
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> triggerBackup(@RequestBody Map<String, String> request) {
        log.info("Manual backup request received.");

        String email = request.get("email");
        String passcode = request.get("passcode");

        Map<String, Object> response = new HashMap<>();

        if (email == null || passcode == null) {
            response.put("success", false);
            response.put("message", "Email and passcode inputs are required.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        // Configuration null-safety check
        if (backupEmail == null || backupPasscode == null) {
            log.error("Backup configuration is missing app.backup.email or app.backup.passcode properties!");
            response.put("success", false);
            response.put("message", "Backup configuration error on the server.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }

        // Validate credentials against environment/default config
        if (!backupEmail.equalsIgnoreCase(email.trim()) || !backupPasscode.equals(passcode)) {
            log.warn("Unauthorized backup attempt with email: {}", email);
            response.put("success", false);
            response.put("message", "Invalid email or passcode.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        try {
            log.info("Credentials validated successfully. Triggering manual backup in the background...");
            
            CompletableFuture.runAsync(() -> {
                try {
                    backupService.performBackupAndSendEmail();
                } catch (Exception e) {
                    log.error("Failed to generate manual backup in background thread: {}", e.getMessage(), e);
                }
            }, emailTaskExecutor);
            
            response.put("success", true);
            response.put("message", "Backup process successfully started in the background. Backup files will be uploaded directly to Google Drive.");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to initiate manual backup: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Failed to trigger backup: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
