package com.tts.sms.scheduler;

import com.tts.sms.service.BackupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler to automatically trigger daily database and file backup to Google Drive & Email
 * Runs every day at 08:00 PM IST
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BackupScheduler {

    private final BackupService backupService;

    /**
     * Automatically triggers the daily backup every day at 8:00 PM IST
     */
    @Scheduled(cron = "${app.backup.cron:0 0 20 * * ?}", zone = "Asia/Kolkata")
    public void scheduleDailyBackup() {
        log.info("[SCHEDULE] Scheduled daily backup execution started...");
        try {
            backupService.performBackupAndSendEmail();
            log.info("[OK] Scheduled daily backup execution completed successfully.");
        } catch (Exception e) {
            log.error("[FAIL] Scheduled daily backup failed: {}", e.getMessage(), e);
        }
    }
}
