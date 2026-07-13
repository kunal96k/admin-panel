package com.tts.sms.scheduler;

import com.tts.sms.service.BackupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler to automatically trigger database and file backup
 * Runs on the last date of each and every month
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BackupScheduler {

    private final BackupService backupService;

    /**
     * Automatically triggers the monthly backup on the last date of every month at 11:00 PM
     */
    @Scheduled(cron = "0 0 23 L * ?", zone = "Asia/Kolkata")
    public void scheduleMonthlyBackup() {
        log.info("⏰ Scheduled monthly backup execution started...");
        try {
            backupService.performBackupAndSendEmail();
            log.info("✅ Scheduled monthly backup execution completed successfully.");
        } catch (Exception e) {
            log.error("❌ Scheduled monthly backup failed: {}", e.getMessage(), e);
        }
    }
}
