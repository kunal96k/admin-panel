package com.tts.sms.service;

import com.tts.sms.dto.WhatsAppAuditContext;
import com.tts.sms.dto.WhatsAppAuditLogSummaryDTO;
import com.tts.sms.dto.WhatsAppKpiStatsDTO;
import com.tts.sms.dto.WhatsAppSendResult;
import com.tts.sms.model.User;
import com.tts.sms.model.WhatsAppAuditLog;
import com.tts.sms.repository.UserRepository;
import com.tts.sms.repository.WhatsAppAuditLogRepository;
import com.tts.sms.specification.WhatsAppAuditLogSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppAuditLogService {

    private final WhatsAppAuditLogRepository logRepository;
    private final UserRepository userRepository;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Resolves the current authenticated user's username.
     */
    public String resolveCurrentUsername() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
                return auth.getName();
            }
        } catch (Exception ignored) {
        }
        return "SYSTEM";
    }

    /**
     * Resolves the current user's full employee name.
     */
    public String resolveCurrentEmployeeName(String username) {
        if (username == null || "SYSTEM".equalsIgnoreCase(username)) {
            return "System Scheduler";
        }
        try {
            Optional<User> userOpt = userRepository.findByUsername(username);
            if (userOpt.isPresent() && userOpt.get().getEmployee() != null) {
                String empName = userOpt.get().getEmployee().getEmployeeName();
                if (empName != null && !empName.isBlank()) {
                    return empName.trim();
                }
            }
        } catch (Exception ignored) {
        }
        return username;
    }

    @jakarta.annotation.PostConstruct
    @Transactional
    public void sanitizeBillingCategories() {
        try {
            logRepository.updateAllToUtility();
            log.info("[WHATSAPP-AUDIT] All template billing categories synchronized to UTILITY.");
        } catch (Exception e) {
            log.warn("[WHATSAPP-AUDIT] Could not sanitize billing categories: {}", e.getMessage());
        }
    }

    /**
     * Saves an audit log entry with independent transaction propagation.
     * Guaranteed fail-safe so it will NEVER interrupt primary business logic.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLog(
            WhatsAppAuditContext context,
            WhatsAppSendResult result,
            Integer httpStatusCode,
            String requestJson,
            String responseJson) {

        try {
            if (context == null) {
                context = new WhatsAppAuditContext();
            }

            String username = context.getTriggeredByUsername();
            if (username == null || username.isBlank()) {
                username = resolveCurrentUsername();
            }

            String employeeName = context.getTriggeredByEmployeeName();
            if (employeeName == null || employeeName.isBlank()) {
                employeeName = resolveCurrentEmployeeName(username);
            }

            String moduleType = context.getModuleType() != null ? context.getModuleType() : "UNKNOWN";
            String templateName = context.getTemplateName() != null ? context.getTemplateName() : "unknown";

            // All Meta WhatsApp templates in system are registered as UTILITY
            String billingCategory = "UTILITY";

            // Determine status
            String status = "FAILED";
            String metaMessageId = null;
            String failureReason = null;

            if (result != null) {
                if (result.isSuccess()) {
                    status = "SUCCESS";
                    metaMessageId = result.getMessageId();
                } else {
                    failureReason = result.getMessage();
                    if (failureReason != null && failureReason.toLowerCase().contains("temporarily disabled")) {
                        status = "BLOCKED";
                    } else {
                        status = "FAILED";
                    }
                }
            } else {
                failureReason = "No result returned from WhatsApp dispatch";
            }

            // Build entity
            WhatsAppAuditLog logEntity = WhatsAppAuditLog.builder()
                    .moduleType(moduleType)
                    .templateName(templateName)
                    .billingCategory(billingCategory)
                    .triggerType(context.getTriggerType() != null ? context.getTriggerType() : "MANUAL")
                    .triggeredByUsername(username)
                    .triggeredByEmployeeName(employeeName)
                    .referenceId(context.getReferenceId())
                    .referenceNumber(context.getReferenceNumber())
                    .studentName(context.getStudentName() != null ? context.getStudentName() : "-")
                    .recipientMobile(context.getRecipientMobile() != null ? context.getRecipientMobile() : (result != null ? result.getRecipientMobile() : "-"))
                    .courseName(context.getCourseName())
                    .attachmentName(context.getAttachmentName())
                    .status(status)
                    .metaMessageId(metaMessageId)
                    .failureReason(failureReason != null && failureReason.length() > 990 ? failureReason.substring(0, 990) : failureReason)
                    .httpStatusCode(httpStatusCode)
                    .requestPayload(requestJson)
                    .responsePayload(responseJson)
                    .build();

            logRepository.save(logEntity);
            log.info("[AUDIT-LOG] Recorded WhatsApp log #{} - Module: {}, Status: {}, Mobile: {}, User: {}",
                    logEntity.getId(), moduleType, status, logEntity.getRecipientMobile(), username);

        } catch (Exception e) {
            log.error("[AUDIT-LOG] Failed to save WhatsApp audit log (fail-safe suppressed): {}", e.getMessage(), e);
        }
    }

    /**
     * Retrieves paginated summary records for the audit log dashboard using JPA Specification.
     */
    @Transactional(readOnly = true)
    public Page<WhatsAppAuditLogSummaryDTO> getLogsWithFilters(
            LocalDate startDate,
            LocalDate endDate,
            String moduleType,
            String status,
            String triggerType,
            String search,
            Pageable pageable) {

        Specification<WhatsAppAuditLog> spec = WhatsAppAuditLogSpecifications.createSpecification(
                startDate, endDate, moduleType, status, triggerType, search);

        try {
            logRepository.updateAllToUtility();
        } catch (Exception ignored) {
        }

        Page<WhatsAppAuditLog> entityPage = logRepository.findAll(spec, pageable);

        return entityPage.map(e -> WhatsAppAuditLogSummaryDTO.builder()
                .id(e.getId())
                .moduleType(e.getModuleType())
                .templateName(e.getTemplateName())
                .billingCategory("UTILITY")
                .triggerType(e.getTriggerType())
                .triggeredByUsername(e.getTriggeredByUsername())
                .triggeredByEmployeeName(e.getTriggeredByEmployeeName())
                .referenceId(e.getReferenceId())
                .referenceNumber(e.getReferenceNumber())
                .studentName(e.getStudentName())
                .recipientMobile(e.getRecipientMobile())
                .courseName(e.getCourseName())
                .attachmentName(e.getAttachmentName())
                .status(e.getStatus())
                .metaMessageId(e.getMetaMessageId())
                .failureReason(e.getFailureReason())
                .httpStatusCode(e.getHttpStatusCode())
                .createdAt(e.getCreatedAt())
                .build());
    }

    /**
     * Retrieves full single log entity including raw request and response payloads.
     */
    @Transactional(readOnly = true)
    public Optional<WhatsAppAuditLog> getLogDetails(Long id) {
        return logRepository.findById(id);
    }

    /**
     * Calculates KPI statistics for the selected period.
     */
    @Transactional
    public WhatsAppKpiStatsDTO getKpiStats(LocalDate startDate, LocalDate endDate) {
        try {
            logRepository.updateAllToUtility();
        } catch (Exception ignored) {
        }

        LocalDateTime startDateTime = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime endDateTime = endDate != null ? endDate.atTime(LocalTime.MAX) : null;

        long total = logRepository.countTotalBetween(startDateTime, endDateTime);
        long success = logRepository.countSuccessfulBetween(startDateTime, endDateTime);
        long failed = logRepository.countFailedBetween(startDateTime, endDateTime);
        long blocked = logRepository.countBlockedBetween(startDateTime, endDateTime);

        // All templates in system are registered as UTILITY
        long utility = logRepository.countSuccessfulBetween(startDateTime, endDateTime);
        long marketing = 0;

        long courseInfo = logRepository.countByModuleTypeBetween("COURSE_INFORMATION", startDateTime, endDateTime);
        long feeReminder = logRepository.countByModuleTypeBetween("FEE_REMINDER", startDateTime, endDateTime);

        return WhatsAppKpiStatsDTO.builder()
                .totalCalls(total)
                .successfulCalls(success)
                .failedCalls(failed)
                .blockedCalls(blocked)
                .utilityCount(utility)
                .marketingCount(marketing)
                .courseInfoCount(courseInfo)
                .feeReminderCount(feeReminder)
                .build();
    }

    /**
     * Generates a UTF-8 BOM CSV export string for Excel billing reconciliation.
     * Applies the exact same JPA Specification filters so ALL matching records
     * across the entire table are exported.
     */
    @Transactional(readOnly = true)
    public String exportLogsToCsv(
            LocalDate startDate,
            LocalDate endDate,
            String moduleType,
            String status,
            String triggerType,
            String search,
            Sort sort) {

        Specification<WhatsAppAuditLog> spec = WhatsAppAuditLogSpecifications.createSpecification(
                startDate, endDate, moduleType, status, triggerType, search);

        Sort effectiveSort = (sort != null && sort.isSorted())
                ? sort
                : Sort.by(Sort.Direction.DESC, "createdAt");

        List<WhatsAppAuditLog> logs = logRepository.findAll(spec, effectiveSort);

        StringBuilder csv = new StringBuilder();
        // UTF-8 Byte Order Mark for Excel
        csv.append("\uFEFF");

        // CSV Header
        csv.append("Log ID,Date & Time,Module Type,Billing Category,Template Name,Trigger Source,Triggered By Username,Triggered By Name,Student Name,Recipient Mobile,Reference No,Course Name,Attachment,Status,Meta Message ID (wamid),HTTP Code,Failure Reason\n");

        for (WhatsAppAuditLog l : logs) {
            csv.append(escapeCsv(String.valueOf(l.getId()))).append(",");
            csv.append(escapeCsv(l.getCreatedAt() != null ? l.getCreatedAt().format(DATE_TIME_FORMATTER) : "")).append(",");
            csv.append(escapeCsv(l.getModuleType())).append(",");
            csv.append(escapeCsv(l.getBillingCategory())).append(",");
            csv.append(escapeCsv(l.getTemplateName())).append(",");
            csv.append(escapeCsv(l.getTriggerType())).append(",");
            csv.append(escapeCsv(l.getTriggeredByUsername())).append(",");
            csv.append(escapeCsv(l.getTriggeredByEmployeeName())).append(",");
            csv.append(escapeCsv(l.getStudentName())).append(",");
            csv.append(escapeCsv(l.getRecipientMobile())).append(",");
            csv.append(escapeCsv(l.getReferenceNumber())).append(",");
            csv.append(escapeCsv(l.getCourseName())).append(",");
            csv.append(escapeCsv(l.getAttachmentName())).append(",");
            csv.append(escapeCsv(l.getStatus())).append(",");
            csv.append(escapeCsv(l.getMetaMessageId())).append(",");
            csv.append(escapeCsv(l.getHttpStatusCode() != null ? String.valueOf(l.getHttpStatusCode()) : "")).append(",");
            csv.append(escapeCsv(l.getFailureReason())).append("\n");
        }

        return csv.toString();
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "\"\"";
        }
        String clean = value.replace("\"", "\"\"");
        return "\"" + clean + "\"";
    }
}
