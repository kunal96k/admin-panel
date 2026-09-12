package com.tts.sms.scheduler;

import com.tts.sms.model.Employee;
import com.tts.sms.repository.EmployeeRepository;
import com.tts.sms.service.EmailTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Scheduler to automatically send birthday wishes to employees
 * (DISABLED: Feature disabled per configuration/request)
 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "app.scheduling.birthday-wishes.enabled",
        havingValue = "true",
        matchIfMissing = false
)
@RequiredArgsConstructor
@Slf4j
public class BirthdayEmailScheduler {

    private final EmployeeRepository employeeRepository;
    private final EmailTemplateService emailTemplateService;

    /**
     * Scheduled task (DISABLED by default)
     */
    // @Scheduled(cron = "0 0 9 * * ?", zone = "Asia/Kolkata")
    public void sendBirthdayWishes() {
        log.info(" Starting birthday email scheduler...");

        try {
            LocalDate today = LocalDate.now();
            int todayMonth = today.getMonthValue();
            int todayDay = today.getDayOfMonth();

            log.info("[DATE] Checking for birthdays on: {}-{}", todayMonth, todayDay);

            // Find all employees with birthdays today
            List<Employee> birthdayEmployees = employeeRepository.findEmployeesWithBirthdayToday(todayMonth, todayDay);

            if (birthdayEmployees.isEmpty()) {
                log.info("[INFO] No birthdays today");
                return;
            }

            log.info("[SUCCESS] Found {} employee(s) with birthday today", birthdayEmployees.size());

            // Send birthday wishes to each employee
            for (Employee employee : birthdayEmployees) {
                try {
                    emailTemplateService.sendBirthdayWishes(
                            employee.getEmailId(),
                            employee.getEmployeeName()
                    );
                    log.info("[OK] Birthday wishes sent to: {} ({})",
                            employee.getEmployeeName(), employee.getEmailId());
                } catch (Exception e) {
                    log.error("[FAIL] Failed to send birthday wishes to {}: {}",
                            employee.getEmployeeName(), e.getMessage());
                }
            }

            log.info(" Birthday email scheduler completed successfully");

        } catch (Exception e) {
            log.error("[FAIL] Error in birthday email scheduler: {}", e.getMessage(), e);
        }
    }

    /**
     * Manual trigger for testing (can be called from a REST endpoint)
     */
    public void sendBirthdayWishesManually() {
        log.info(" Manual birthday email trigger started...");
        sendBirthdayWishes();
    }
}