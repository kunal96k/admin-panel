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
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Scheduler to send automated daily fee due reminders to students
 * with countdown: 5, 4, 3, 2, 1 days before installment due date,
 * and a final reminder on the due date itself.
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
    private final com.tts.sms.service.WhatsAppService whatsAppService;

    /**
     * Isolated single-instance in-memory duplicate guard to prevent duplicate WhatsApp fee reminders on the same day.
     * NOTE: This guard stores records in JVM memory for the current process lifecycle and does NOT
     * provide persistent or distributed multi-instance duplicate protection.
     */
    public static class InMemoryFeeReminderDuplicateGuard {
        private final Set<String> sentKeys = Collections.synchronizedSet(new HashSet<>());

        public boolean hasBeenSentToday(String regNo, LocalDate targetDueDate, LocalDate executionDate) {
            return sentKeys.contains(buildKey(regNo, targetDueDate, executionDate));
        }

        public void markAsSent(String regNo, LocalDate targetDueDate, LocalDate executionDate) {
            sentKeys.add(buildKey(regNo, targetDueDate, executionDate));
        }

        public void clear() {
            sentKeys.clear();
        }

        public int size() {
            return sentKeys.size();
        }

        private String buildKey(String regNo, LocalDate targetDueDate, LocalDate executionDate) {
            return (regNo != null ? regNo.trim() : "") + ":" + targetDueDate + ":" + executionDate;
        }
    }

    private final InMemoryFeeReminderDuplicateGuard duplicateGuard = new InMemoryFeeReminderDuplicateGuard();

    public InMemoryFeeReminderDuplicateGuard getDuplicateGuard() {
        return this.duplicateGuard;
    }

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
        private Integer daysRemaining;
    }

    /**
     * Scheduled job running daily at 11:00 AM Asia/Kolkata (Indian Standard Time).
     * Executes daily countdown reminders for installments due in 5, 4, 3, 2, 1 days,
     * and on the due date itself (final reminder).
     */
    @Scheduled(cron = "${app.scheduling.fee-reminder.cron:0 0 11 * * ?}", zone = "Asia/Kolkata")
    public void sendDailyFeeDueReminders() {
        log.info("🔔 [DAILY SCHEDULER] Running automated daily fee due countdown reminders (5, 4, 3, 2, 1 days before & due date)...");
        sendDailyCountdownFeeDueReminders(LocalDate.now());
    }

    /**
     * Executes countdown reminders from 5 days before down to 0 days (due today).
     *
     * @param today base date for countdown (defaults to LocalDate.now())
     * @return summary of countdown run across all days
     */
    public Map<String, Object> sendDailyCountdownFeeDueReminders(LocalDate today) {
        if (today == null) {
            today = LocalDate.now();
        }
        log.info("==================================================================");
        log.info("🔔 [COUNTDOWN] Executing Daily Fee Due Reminders for Countdown 5..0 days from: {}", today);
        log.info("==================================================================");

        Map<String, Object> overallResult = new LinkedHashMap<>();
        overallResult.put("executionDate", today.toString());

        boolean emailEnabled = systemConfigurationService.isFeeReminderEmailEnabled();
        boolean whatsAppEnabled = systemConfigurationService.isFeeReminderWhatsAppEnabled();

        if (!emailEnabled && !whatsAppEnabled) {
            log.info("🚫 [GLOBAL DISABLED] Both fee reminder emails and WhatsApp messages are globally disabled in System Configuration. Skipping execution.");
            overallResult.put("status", "DISABLED");
            overallResult.put("message", "Fee reminder emails and WhatsApp messages are globally disabled in system configuration.");
            overallResult.put("totalSent", 0);
            overallResult.put("totalWhatsAppSent", 0);
            return overallResult;
        }

        int totalSentAcrossAllDays = 0;
        int totalWhatsAppSentAcrossAllDays = 0;
        List<Map<String, Object>> dailyBreakdown = new ArrayList<>();

        // Countdown from 5 days before, 4, 3, 2, 1 down to 0 (Due Today)
        for (int daysRemaining = 5; daysRemaining >= 0; daysRemaining--) {
            LocalDate targetDueDate = today.plusDays(daysRemaining);
            log.info("📅 [COUNTDOWN DAY] Checking {} days remaining (Due Date: {})", daysRemaining, targetDueDate);
            Map<String, Object> dayResult = sendFeeRemindersForDate(targetDueDate, daysRemaining);
            dailyBreakdown.add(dayResult);
            int sentForDay = (int) dayResult.getOrDefault("sentCount", 0);
            int waSentForDay = (int) dayResult.getOrDefault("whatsappSentCount", 0);
            totalSentAcrossAllDays += sentForDay;
            totalWhatsAppSentAcrossAllDays += waSentForDay;
        }

        overallResult.put("status", "SUCCESS");
        overallResult.put("totalSent", totalSentAcrossAllDays);
        overallResult.put("totalEmailsSent", totalSentAcrossAllDays);
        overallResult.put("totalWhatsAppSent", totalWhatsAppSentAcrossAllDays);
        overallResult.put("breakdown", dailyBreakdown);

        log.info("🏁 [COUNTDOWN COMPLETED] Finished daily countdown fee reminders. Emails queued: {}, WhatsApp messages sent: {}",
                totalSentAcrossAllDays, totalWhatsAppSentAcrossAllDays);
        return overallResult;
    }

    /**
     * Sends fee due reminder emails for any given target date.
     * Calculates daysRemaining relative to today.
     *
     * @param targetDueDate the due date to check
     * @return Map containing execution statistics
     */
    public Map<String, Object> sendFeeRemindersForDate(LocalDate targetDueDate) {
        Integer daysRemaining = null;
        if (targetDueDate != null) {
            daysRemaining = (int) ChronoUnit.DAYS.between(LocalDate.now(), targetDueDate);
        }
        return sendFeeRemindersForDate(targetDueDate, daysRemaining);
    }

    /**
     * Sends fee due reminder emails for a target due date with an explicit daysRemaining offset.
     *
     * @param targetDueDate the due date to check
     * @param daysRemaining days remaining until due date (5, 4, 3, 2, 1, 0)
     * @return Map containing execution statistics
     */
    public Map<String, Object> sendFeeRemindersForDate(LocalDate targetDueDate, Integer daysRemaining) {
        String label = (daysRemaining != null && daysRemaining == 0)
                ? "DUE TODAY (FINAL REMINDER)"
                : (daysRemaining != null ? daysRemaining + " Days Left" : "Notice");

        log.info("==================================================================");
        log.info("📧 [NOTIF] Processing Fee Due Reminders for Due Date: {} [{}]", targetDueDate, label);
        log.info("==================================================================");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("targetDueDate", targetDueDate != null ? targetDueDate.toString() : null);
        result.put("daysRemaining", daysRemaining);
        result.put("reminderLabel", label);

        boolean emailEnabled = systemConfigurationService.isFeeReminderEmailEnabled();
        boolean whatsAppEnabled = systemConfigurationService.isFeeReminderWhatsAppEnabled();

        // Global master toggle check: skip only if BOTH channels are disabled
        if (!emailEnabled && !whatsAppEnabled) {
            log.info("🚫 [GLOBAL DISABLED] Both fee reminder emails and WhatsApp messages are globally disabled in System Configuration. Skipping execution.");
            result.put("status", "DISABLED");
            result.put("message", "Fee reminder emails and WhatsApp messages are globally disabled in system configuration.");
            result.put("sentCount", 0);
            result.put("whatsappSentCount", 0);
            return result;
        }

        int sentCount = 0;
        int skippedNoEmailCount = 0;
        int skippedNoAdmissionCount = 0;
        int skippedDisabledCount = 0;
        int skippedNonEligibleCategoryCount = 0;
        List<String> sentToEmails = new ArrayList<>();
        List<String> skippedStudents = new ArrayList<>();

        // WhatsApp execution statistics
        int sentWhatsAppCount = 0;
        int skippedNoMobileWhatsAppCount = 0;
        int skippedDuplicateWhatsAppCount = 0;
        int failedWhatsAppCount = 0;
        List<String> sentToWhatsAppList = new ArrayList<>();

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
                            .daysRemaining(daysRemaining != null ? daysRemaining : (int) ChronoUnit.DAYS.between(LocalDate.now(), targetDueDate))
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
                            .daysRemaining(daysRemaining != null ? daysRemaining : (int) ChronoUnit.DAYS.between(LocalDate.now(), targetDueDate))
                            .build());
                }
            }

            log.info("🎯 [TARGET] Found {} unique student(s) with fee due date: {} [{}]", reminderMap.size(), targetDueDate, label);

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
                    log.info("[INFO] [INDIVIDUAL DISABLED] Fee reminders disabled for student: {} ({}) - Skipping notifications",
                            admission.getFullName(), regNo);
                    skippedDisabledCount++;
                    skippedStudents.add(regNo + " (" + admission.getFullName() + " - Individually Disabled)");
                    continue;
                }

                // Student category check: ONLY 'NEW_STUDENT' and 'PURSUING' are eligible
                String studentCategory = admission.getStudentCategory();
                if (!isEligibleCategory(studentCategory)) {
                    log.info("[INFO] [CATEGORY SKIPPED] Student: {} ({}) has category '{}' (only NEW_STUDENT / PURSUING eligible) - Skipping reminders",
                            admission.getFullName(), regNo, studentCategory);
                    skippedNonEligibleCategoryCount++;
                    skippedStudents.add(regNo + " (" + admission.getFullName() + " - Ineligible Category: " + studentCategory + ")");
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

                // ==================== 1. EMAIL NOTIFICATION CHANNEL ====================
                if (emailEnabled) {
                    String recipientEmail = null;
                    if (admission.getEmailPrimary() != null && !admission.getEmailPrimary().trim().isEmpty()) {
                        recipientEmail = admission.getEmailPrimary().trim();
                    } else if (admission.getEmailSecondary() != null && !admission.getEmailSecondary().trim().isEmpty()) {
                        recipientEmail = admission.getEmailSecondary().trim();
                    }

                    if (recipientEmail != null && !recipientEmail.isEmpty() && recipientEmail.contains("@")) {
                        emailTemplateService.sendFeeDueReminderEmail(
                                recipientEmail,
                                studentName,
                                regNo,
                                courseName,
                                info.getInstallmentNumber(),
                                info.getDueAmount(),
                                info.getDueDate(),
                                totalDue,
                                info.getDaysRemaining()
                        );

                        sentCount++;
                        String reminderType = (info.getDaysRemaining() != null && info.getDaysRemaining() == 0)
                                ? "DUE TODAY (FINAL REMINDER)"
                                : (info.getDaysRemaining() + " days before");
                        sentToEmails.add(regNo + " -> " + recipientEmail + " (" + studentName + ", ₹" + info.getDueAmount() + ", " + reminderType + ")");
                        log.info("✅ Queued fee reminder email [{}] to: {} for student: {} ({}), Due Amount: ₹{}",
                                reminderType, recipientEmail, studentName, regNo, info.getDueAmount());
                    } else {
                        log.info("[INFO] [SKIPPED EMAIL] No email provided in admission for student: {} ({}) - Skipping email notification",
                                admission.getFullName(), regNo);
                        skippedNoEmailCount++;
                        skippedStudents.add(regNo + " (" + admission.getFullName() + " - No Email)");
                    }
                }

                // ==================== 2. OPTIONAL WHATSAPP CHANNEL ====================
                if (whatsAppEnabled) {
                    // Preserve existing mobile fallback: mobilePrimary -> mobileSecondary
                    String recipientMobile = (admission.getMobilePrimary() != null && !admission.getMobilePrimary().isBlank())
                            ? admission.getMobilePrimary().trim()
                            : (admission.getMobileSecondary() != null ? admission.getMobileSecondary().trim() : null);

                    if (recipientMobile == null || recipientMobile.isBlank()) {
                        log.info("[INFO] [SKIPPED WHATSAPP] No primary/secondary mobile for student: {} ({})",
                                admission.getFullName(), regNo);
                        skippedNoMobileWhatsAppCount++;
                    } else if (duplicateGuard.hasBeenSentToday(regNo, targetDueDate, LocalDate.now())) {
                        log.info("[INFO] [WHATSAPP GUARD] Reminder already sent today for student: {} ({}), Due Date: {}. Skipping.",
                                admission.getFullName(), regNo, targetDueDate);
                        skippedDuplicateWhatsAppCount++;
                    } else {
                        // Dynamic payment due status string
                        String paymentDueStatus;
                        if (daysRemaining != null && daysRemaining == 0) {
                            paymentDueStatus = "is due today.";
                        } else if (daysRemaining != null && daysRemaining == 1) {
                            paymentDueStatus = "is due tomorrow.";
                        } else if (daysRemaining != null && daysRemaining > 1) {
                            paymentDueStatus = "is due in " + daysRemaining + " days.";
                        } else {
                            paymentDueStatus = "is due soon.";
                        }

                        String installmentText = "Installment #" + (info.getInstallmentNumber() != null ? info.getInstallmentNumber() : 1);
                        String installmentAmountText = String.format("%.2f", info.getDueAmount() != null ? info.getDueAmount() : 0.0);
                        String dueDateText = info.getDueDate() != null
                                ? info.getDueDate().format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                                : "";
                        String outstandingBalanceText = String.format("%.2f", totalDue != null ? totalDue : 0.0);

                        com.tts.sms.dto.WhatsAppAuditContext auditContext = com.tts.sms.dto.WhatsAppAuditContext.builder()
                                .moduleType("FEE_REMINDER")
                                .templateName("crm_fee_reminder_utility")
                                .billingCategory("UTILITY")
                                .triggerType("AUTOMATED_SCHEDULER")
                                .triggeredByUsername("SYSTEM")
                                .triggeredByEmployeeName("System Scheduler")
                                .referenceId(admission.getId())
                                .referenceNumber(regNo)
                                .studentName(studentName)
                                .recipientMobile(recipientMobile)
                                .courseName(courseName != null ? courseName : "-")
                                .build();

                        com.tts.sms.dto.WhatsAppSendResult waResult = whatsAppService.sendFeeReminder(
                                recipientMobile,
                                studentName,
                                courseName != null ? courseName : "-",
                                paymentDueStatus,
                                regNo,
                                installmentText,
                                installmentAmountText,
                                dueDateText,
                                outstandingBalanceText,
                                auditContext
                        );

                        if (waResult != null && waResult.isSuccess()) {
                            duplicateGuard.markAsSent(regNo, targetDueDate, LocalDate.now());
                            sentWhatsAppCount++;
                            sentToWhatsAppList.add(regNo + " -> " + waResult.getRecipientMobile() + " (" + studentName + ", ₹" + installmentAmountText + ")");
                            log.info("✅ Sent fee reminder WhatsApp to: {} for student: {} ({}), Due: ₹{}",
                                    waResult.getRecipientMobile(), studentName, regNo, installmentAmountText);
                        } else {
                            failedWhatsAppCount++;
                            log.warn("⚠️ Failed to send fee reminder WhatsApp to student: {} ({}): {}",
                                    studentName, regNo, waResult != null ? waResult.getMessage() : "Unknown error");
                        }
                    }
                }
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

            // WhatsApp stats
            result.put("whatsappSentCount", sentWhatsAppCount);
            result.put("whatsappSkippedDuplicateCount", skippedDuplicateWhatsAppCount);
            result.put("whatsappSkippedNoMobileCount", skippedNoMobileWhatsAppCount);
            result.put("whatsappFailedCount", failedWhatsAppCount);
            result.put("whatsappSentDetails", sentToWhatsAppList);

            log.info("[SUCCESS] Fee reminder execution finished. Email Sent: {}, WhatsApp Sent: {}, Skipped (No email): {}, Skipped (No WhatsApp mobile): {}",
                    sentCount, sentWhatsAppCount, skippedNoEmailCount, skippedNoMobileWhatsAppCount);

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
