package com.tts.sms.scheduler;

import com.tts.sms.service.BackupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler to automatically trigger daily database and file backup to Google Drive & Email
 * Runs every day at 02:00 AM IST
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BackupScheduler {

    private final BackupService backupService;

    /**
     * Automatically triggers the daily backup every night at 2:00 AM IST
     */
    @Scheduled(cron = "0 0 2 * * ?", zone = "Asia/Kolkata")
    public void scheduleDailyBackup() {
        log.info("⏰ Scheduled daily backup execution started...");
        try {
            backupService.performBackupAndSendEmail();
            log.info("✅ Scheduled daily backup execution completed successfully.");
        } catch (Exception e) {
            log.error("❌ Scheduled daily backup failed: {}", e.getMessage(), e);
        }
    }
}
