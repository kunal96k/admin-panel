package com.tts.sms.scheduler;

import com.tts.sms.service.StudentCategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily scheduler to auto-recategorize all students at midnight.
 *
 * Key behaviour:
 *  - NEW_STUDENT (REG* number) admitted within last 7 days -> stays NEW_STUDENT
 *  - NEW_STUDENT older than 7 days -> promoted to PURSUING automatically
 *  - CANCELLED and COMPLETED are terminal states -- never overwritten
 *  - OLD_STUDENT stays locked by cutoff date logic
 *
 * Runs every day at midnight IST (Asia/Kolkata).
 * Cron can be overridden in application.properties via app.category.cron
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StudentCategoryScheduler {

    private final StudentCategoryService studentCategoryService;

    /**
     * Runs daily at midnight IST.
     * Promotes NEW_STUDENT -> PURSUING after the 7-day window expires.
     */
    @Scheduled(cron = "${app.category.cron:0 0 0 * * ?}", zone = "Asia/Kolkata")
    public void dailyStudentRecategorization() {
        log.info("[SCHEDULE] Daily student recategorization started...");
        try {
            studentCategoryService.recategorizeAllStudents();
            log.info("[OK] Daily student recategorization completed successfully.");
        } catch (Exception e) {
            log.error("[FAIL] Daily student recategorization failed: {}", e.getMessage(), e);
        }
    }
}