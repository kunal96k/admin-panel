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
 * Runs daily at 9:00 AM
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BirthdayEmailScheduler {

    private final EmployeeRepository employeeRepository;
    private final EmailTemplateService emailTemplateService;

    /**
     * Scheduled task that runs every day at 9:00 AM
     * Checks for employees with birthdays today and sends them birthday wishes
     */
    @Scheduled(cron = "0 39 23 * * ?", zone = "Asia/Kolkata")  // Runs at 9:00 AM every day
    public void sendBirthdayWishes() {
        log.info(" Starting birthday email scheduler...");

        try {
            LocalDate today = LocalDate.now();
            int todayMonth = today.getMonthValue();
            int todayDay = today.getDayOfMonth();

            log.info("📅 Checking for birthdays on: {}-{}", todayMonth, todayDay);

            // Find all employees with birthdays today
            List<Employee> birthdayEmployees = employeeRepository.findEmployeesWithBirthdayToday(todayMonth, todayDay);

            if (birthdayEmployees.isEmpty()) {
                log.info("ℹ️ No birthdays today");
                return;
            }

            log.info("🎉 Found {} employee(s) with birthday today", birthdayEmployees.size());

            // Send birthday wishes to each employee
            for (Employee employee : birthdayEmployees) {
                try {
                    emailTemplateService.sendBirthdayWishes(
                            employee.getEmailId(),
                            employee.getEmployeeName()
                    );
                    log.info("✅ Birthday wishes sent to: {} ({})",
                            employee.getEmployeeName(), employee.getEmailId());
                } catch (Exception e) {
                    log.error("❌ Failed to send birthday wishes to {}: {}",
                            employee.getEmployeeName(), e.getMessage());
                }
            }

            log.info(" Birthday email scheduler completed successfully");

        } catch (Exception e) {
            log.error("❌ Error in birthday email scheduler: {}", e.getMessage(), e);
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