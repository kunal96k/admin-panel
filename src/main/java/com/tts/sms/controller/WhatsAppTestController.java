package com.tts.sms.controller;

import com.tts.sms.dto.WhatsAppSendResult;
import com.tts.sms.scheduler.FeeDueReminderScheduler;
import com.tts.sms.service.WhatsAppService;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Safe Development-Only Testing Controller for Meta WhatsApp templates.
 *
 * Strict Security Rules:
 * - Disabled in production by default (app.whatsapp.test-endpoints-enabled=false).
 * - Never returns or exposes the Meta access token.
 * - Uses exact Meta template names:
 *     Course: crm_course_information (en_US)
 *     Fee:    crm_fee_reminder_utility (en_US)
 * - Allows testing templates directly without triggering the production scheduler.
 */
@Slf4j
@RestController
@RequestMapping("/api/test/whatsapp")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@ConditionalOnProperty(name = "app.whatsapp.test-endpoints-enabled", havingValue = "true")
public class WhatsAppTestController {

    private final WhatsAppService whatsAppService;
    private final FeeDueReminderScheduler feeDueReminderScheduler;

    @Value("${app.whatsapp.test-endpoints-enabled:false}")
    private boolean testEndpointsEnabled;

    @Data
    public static class CourseInfoTestRequest {
        @NotBlank(message = "Recipient mobile is required")
        private String mobile;
        private String studentName;
        private String courseList;
        private String documentUrl;
    }

    @Data
    public static class FeeReminderTestRequest {
        @NotBlank(message = "Recipient mobile is required")
        private String mobile;
        private String studentName;
        private String courseName;
        private String paymentDueStatus;
        private String registrationNumber;
        private String installment;
        private String installmentAmount;
        private String dueDate;
        private String outstandingBalance;
    }

    /**
     * Test Course Information Meta Template: crm_course_information
     * Parameters:
     * 1: student_name
     * 2: course_list
     * Header: Document (optional/provided via documentUrl)
     */
    @PostMapping("/course-information")
    public ResponseEntity<WhatsAppSendResult> testCourseInformation(@RequestBody CourseInfoTestRequest request) {
        if (!testEndpointsEnabled) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(WhatsAppSendResult.failure("Test endpoints are disabled on this environment", null));
        }

        log.info("[TEST-WHATSAPP] Testing course-information template for mobile: {}",
                WhatsAppService.maskPhoneNumber(request.getMobile()));

        WhatsAppSendResult result = whatsAppService.sendCourseInformationDirect(
                request.getMobile(),
                request.getStudentName(),
                request.getCourseList(),
                request.getDocumentUrl()
        );

        return ResponseEntity.ok(result);
    }

    /**
     * Test Fee Due Reminder Meta Template: crm_fee_reminder_utility
     * Parameters:
     * 1: student_name
     * 2: course_name
     * 3: payment_due_status
     * 4: registration_number
     * 5: installment
     * 6: installment_amount
     * 7: due_date
     * 8: outstanding_balance
     * Header: NONE
     */
    @PostMapping("/fee-reminder")
    public ResponseEntity<WhatsAppSendResult> testFeeReminder(@RequestBody FeeReminderTestRequest request) {
        if (!testEndpointsEnabled) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(WhatsAppSendResult.failure("Test endpoints are disabled on this environment", null));
        }

        log.info("[TEST-WHATSAPP] Testing fee-reminder template for mobile: {}",
                WhatsAppService.maskPhoneNumber(request.getMobile()));

        WhatsAppSendResult result = whatsAppService.sendFeeReminder(
                request.getMobile(),
                request.getStudentName(),
                request.getCourseName(),
                request.getPaymentDueStatus() != null ? request.getPaymentDueStatus() : "is due in 5 days.",
                request.getRegistrationNumber() != null ? request.getRegistrationNumber() : "TTS/2026/001",
                request.getInstallment() != null ? request.getInstallment() : "Installment #1",
                request.getInstallmentAmount() != null ? request.getInstallmentAmount() : "5000.00",
                request.getDueDate() != null ? request.getDueDate() : "15-10-2026",
                request.getOutstandingBalance() != null ? request.getOutstandingBalance() : "15000.00"
        );

        return ResponseEntity.ok(result);
    }

    /**
     * Clear in-memory duplicate guard during development/testing.
     */
    @PostMapping("/clear-duplicate-guard")
    public ResponseEntity<Map<String, Object>> clearDuplicateGuard() {
        feeDueReminderScheduler.getDuplicateGuard().clear();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "In-memory fee reminder duplicate guard cleared successfully"
        ));
    }
}
