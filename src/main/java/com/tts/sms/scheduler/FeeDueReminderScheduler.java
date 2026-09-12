package com.tts.sms.scheduler;

import com.tts.sms.model.Admission;
import com.tts.sms.model.FeeInstallment;
import com.tts.sms.model.Fees;
import com.tts.sms.repository.AdmissionRepository;
import com.tts.sms.repository.FeeInstallmentRepository;
import com.tts.sms.repository.FeesRepository;
import com.tts.sms.service.EmailTemplateService;
import com.tts.sms.service.SystemConfigurationService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

/**
 * Scheduler to send automated daily fee due reminders to students
 * exactly 5 days before their installment/fee due date.
 *
 * Runs daily at 11:00 AM IST (Asia/Kolkata).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FeeDueReminderScheduler {

    private final FeeInstallmentRepository feeInstallmentRepository;
    private final FeesRepository feesRepository;
    private final AdmissionRepository admissionRepository;
    private final EmailTemplateService emailTemplateService;
    private final SystemConfigurationService systemConfigurationService;

    /**
     * DTO to aggregate reminder information per student
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StudentReminderInfo {
        private String registrationNumber;
        private String studentName;
        private String courseName;
        private Integer installmentNumber;
        private Double dueAmount;
        private LocalDate dueDate;
        private Double totalDue;
    }

    /**
     * Scheduled job running daily at 11:00 AM Asia/Kolkata (Indian Standard Time)
     * Checks for fee installments and fees due exactly 5 days from today
     */
    @Scheduled(cron = "${app.scheduling.fee-reminder.cron:0 0 11 * * ?}", zone = "Asia/Kolkata")
    public void sendDailyFeeDueReminders() {
        LocalDate targetDueDate = LocalDate.now().plusDays(5);
        log.info("[NOTIF] [DAILY SCHEDULER] Running automated fee due reminder for due date: {} (today + 5 days)", targetDueDate);
        sendFeeRemindersForDate(targetDueDate);
    }

    /**
     * Sends fee due reminder emails for any given target date.
     * Can be invoked manually from REST endpoints for testing or on-demand execution.
     *
     * @param targetDueDate the due date to check (normally today + 5 days)
     * @return Map containing execution statistics
     */
    public Map<String, Object> sendFeeRemindersForDate(LocalDate targetDueDate) {
        log.info("==================================================================");
        log.info("[NOTIF] Processing Fee Due Reminders for Target Due Date: {}", targetDueDate);
        log.info("==================================================================");

        Map<String, Object> result = new HashMap<>();
        result.put("targetDueDate", targetDueDate.toString());

        // Global master toggle check
        if (!systemConfigurationService.isFeeReminderEmailEnabled()) {
            log.info("[DENIED] [GLOBAL DISABLED] Automated fee reminder emails are globally disabled in System Configuration. Skipping execution.");
            result.put("status", "DISABLED");
            result.put("message", "Fee reminder emails are globally disabled in system configuration.");
            result.put("sentCount", 0);
            return result;
        }

        int sentCount = 0;
        int skippedNoEmailCount = 0;
        int skippedNoAdmissionCount = 0;
        int skippedDisabledCount = 0;
        int skippedNonEligibleCategoryCount = 0;
        List<String> sentToEmails = new ArrayList<>();
        List<String> skippedStudents = new ArrayList<>();

        try {
            // Map of registrationNumber -> StudentReminderInfo to avoid sending multiple emails for the same student
            Map<String, StudentReminderInfo> reminderMap = new LinkedHashMap<>();

            // 1. Check FeeInstallments due on targetDueDate
            List<FeeInstallment> pendingInstallments = feeInstallmentRepository.findPendingInstallmentsDueOnDate(targetDueDate);
            log.info("[INFO] Found {} pending installment(s) with due date = {}", pendingInstallments.size(), targetDueDate);

            for (FeeInstallment installment : pendingInstallments) {
                String regNo = installment.getRegistrationNumber();
                if (regNo == null || regNo.trim().isEmpty()) {
                    continue;
                }

                Double installmentDue = installment.getRemainingAmount();
                if (installmentDue == null || installmentDue <= 0) {
                    Double amount = installment.getAmount() != null ? installment.getAmount() : 0.0;
                    Double paid = installment.getPaidAmount() != null ? installment.getPaidAmount() : 0.0;
                    installmentDue = Math.max(0.0, amount - paid);
                }

                if (installmentDue <= 0.01) {
                    continue; // Skip if nothing due
                }

                if (!reminderMap.containsKey(regNo)) {
                    reminderMap.put(regNo, StudentReminderInfo.builder()
                            .registrationNumber(regNo)
                            .installmentNumber(installment.getInstallmentNumber())
                            .dueAmount(installmentDue)
                            .dueDate(installment.getDueDate())
                            .build());
                } else {
                    // Accumulate if student has multiple installments on the same date
                    StudentReminderInfo existing = reminderMap.get(regNo);
                    existing.setDueAmount(existing.getDueAmount() + installmentDue);
                }
            }

            // 2. Check Fees table for students whose primary dueDate is targetDueDate
            List<Fees> pendingFees = feesRepository.findPendingFeesDueOnDate(targetDueDate);
            log.info("[INFO] Found {} pending fee record(s) with due date = {}", pendingFees.size(), targetDueDate);

            for (Fees fees : pendingFees) {
                String regNo = fees.getRegistrationNumber();
                if (regNo == null || regNo.trim().isEmpty()) {
                    continue;
                }

                if (!reminderMap.containsKey(regNo) && fees.getFeesDue() != null && fees.getFeesDue() > 0.01) {
                    reminderMap.put(regNo, StudentReminderInfo.builder()
                            .registrationNumber(regNo)
                            .studentName(fees.getStudentName())
                            .courseName(fees.getCourse())
                            .dueAmount(fees.getFeesDue())
                            .dueDate(fees.getDueDate())
                            .totalDue(fees.getFeesDue())
                            .build());
                }
            }

            log.info("[TARGET] Total unique student(s) eligible for 5-day fee reminder: {}", reminderMap.size());

            // 3. Process each eligible student
            for (StudentReminderInfo info : reminderMap.values()) {
                String regNo = info.getRegistrationNumber();

                // Fetch student admission details
                Admission admission = admissionRepository.findByRegistrationNumberAndIsDeletedFalse(regNo);
                if (admission == null) {
                    log.warn("[WARN] Admission record not found for registration number: {}. Skipping.", regNo);
                    skippedNoAdmissionCount++;
                    skippedStudents.add(regNo + " (No Admission Record)");
                    continue;
                }

                // Individual student toggle check: do not send if disabled for this student
                if (!admission.isFeeReminderEmailEnabled()) {
                    log.info("[INFO] [INDIVIDUAL DISABLED] Fee reminder emails disabled for student: {} ({}) - Skipping email notification",
                            admission.getFullName(), regNo);
                    skippedDisabledCount++;
                    skippedStudents.add(regNo + " (" + admission.getFullName() + " - Individually Disabled)");
                    continue;
                }

                // Student category check: ONLY 'NEW_STUDENT' and 'PURSUING' are eligible
                String studentCategory = admission.getStudentCategory();
                if (!isEligibleCategory(studentCategory)) {
                    log.info("[INFO] [CATEGORY SKIPPED] Student: {} ({}) has category '{}' (only NEW_STUDENT / PURSUING eligible) - Skipping email reminder",
                            admission.getFullName(), regNo, studentCategory);
                    skippedNonEligibleCategoryCount++;
                    skippedStudents.add(regNo + " (" + admission.getFullName() + " - Ineligible Category: " + studentCategory + ")");
                    continue;
                }

                // Strict rule: check if email is provided in admission
                String recipientEmail = null;
                if (admission.getEmailPrimary() != null && !admission.getEmailPrimary().trim().isEmpty()) {
                    recipientEmail = admission.getEmailPrimary().trim();
                } else if (admission.getEmailSecondary() != null && !admission.getEmailSecondary().trim().isEmpty()) {
                    recipientEmail = admission.getEmailSecondary().trim();
                }

                // If no email present in admission, DO NOT send email to any account
                if (recipientEmail == null || recipientEmail.isEmpty() || !recipientEmail.contains("@")) {
                    log.info("[INFO] [SKIPPED] No email provided in admission for student: {} ({}) - Skipping email notification",
                            admission.getFullName(), regNo);
                    skippedNoEmailCount++;
                    skippedStudents.add(regNo + " (" + admission.getFullName() + " - No Email)");
                    continue;
                }

                // Enrich missing student / course / totalDue data
                String studentName = (admission.getFullName() != null && !admission.getFullName().trim().isEmpty())
                        ? admission.getFullName().trim()
                        : (info.getStudentName() != null ? info.getStudentName() : "Student");

                String courseName = null;
                if (admission.getPackageName() != null && !admission.getPackageName().trim().isEmpty()) {
                    courseName = admission.getPackageName().trim();
                } else if (admission.getCourses() != null && !admission.getCourses().isEmpty()) {
                    List<String> validCourses = admission.getCourses().stream()
                            .filter(c -> c != null && !c.trim().isEmpty() && !"null".equalsIgnoreCase(c.trim()))
                            .toList();
                    if (!validCourses.isEmpty()) {
                        courseName = String.join(", ", validCourses);
                    }
                }

                if (courseName == null || courseName.trim().isEmpty()) {
                    if (info.getCourseName() != null && !info.getCourseName().trim().isEmpty()) {
                        courseName = info.getCourseName().trim();
                    } else {
                        courseName = feesRepository.findByRegistrationNumberAndIsDeletedFalse(regNo)
                                .map(Fees::getCourse)
                                .filter(c -> c != null && !c.trim().isEmpty())
                                .orElse(null);
                    }
                }

                Double totalDue = info.getTotalDue();
                if (totalDue == null) {
                    totalDue = feesRepository.findByRegistrationNumberAndIsDeletedFalse(regNo)
                            .map(Fees::getFeesDue)
                            .orElse(info.getDueAmount());
                }

                // Send the email asynchronously
                emailTemplateService.sendFeeDueReminderEmail(
                        recipientEmail,
                        studentName,
                        regNo,
                        courseName,
                        info.getInstallmentNumber(),
                        info.getDueAmount(),
                        info.getDueDate(),
                        totalDue
                );

                sentCount++;
                sentToEmails.add(regNo + " -> " + recipientEmail + " (" + studentName + ", ₹" + info.getDueAmount() + ")");
                log.info("[OK] Queued 5-day fee reminder email to: {} for student: {} ({}), Due Amount: ₹{}",
                        recipientEmail, studentName, regNo, info.getDueAmount());
            }

            result.put("status", "SUCCESS");
            result.put("totalEligible", reminderMap.size());
            result.put("sentCount", sentCount);
            result.put("skippedNoEmailCount", skippedNoEmailCount);
            result.put("skippedNoAdmissionCount", skippedNoAdmissionCount);
            result.put("skippedDisabledCount", skippedDisabledCount);
            result.put("skippedNonEligibleCategoryCount", skippedNonEligibleCategoryCount);
            result.put("sentDetails", sentToEmails);
            result.put("skippedDetails", skippedStudents);

            log.info("[SUCCESS] Fee reminder execution finished. Sent: {}, Skipped (No email): {}, Skipped (No admission): {}, Skipped (Individually disabled): {}, Skipped (Ineligible category): {}",
                    sentCount, skippedNoEmailCount, skippedNoAdmissionCount, skippedDisabledCount, skippedNonEligibleCategoryCount);

        } catch (Exception e) {
            log.error("[FAIL] Error during fee due reminder scheduler execution: {}", e.getMessage(), e);
            result.put("status", "ERROR");
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * Checks if a student's category is eligible for automated fee reminder emails.
     * Only "NEW_STUDENT" and "PURSUING" are eligible.
     *
     * @param category the student's category from Admission record
     * @return true if NEW_STUDENT or PURSUING, false otherwise
     */
    public static boolean isEligibleCategory(String category) {
        if (category == null || category.trim().isEmpty()) {
            return false;
        }
        String normalized = category.trim().toUpperCase().replace(" ", "_");
        return "NEW_STUDENT".equals(normalized) || "PURSUING".equals(normalized);
    }
}
