package com.tts.sms.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Audit log entity tracking every WhatsApp API message dispatch.
 * Used for user accountability, billing reconciliation with Meta invoices,
 * and delivery troubleshooting.
 * 
 * Accessible strictly to SUPER_ADMIN.
 */
@Entity
@Table(name = "whatsapp_api_logs", indexes = {
        @Index(name = "idx_wa_log_created_at", columnList = "created_at"),
        @Index(name = "idx_wa_log_module_type", columnList = "module_type"),
        @Index(name = "idx_wa_log_status", columnList = "status"),
        @Index(name = "idx_wa_log_recipient_mobile", columnList = "recipient_mobile"),
        @Index(name = "idx_wa_log_reference_num", columnList = "reference_number"),
        @Index(name = "idx_wa_log_meta_msg_id", columnList = "meta_message_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WhatsAppAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Module or functional origin: COURSE_INFORMATION, FEE_REMINDER, TEST
     */
    @Column(name = "module_type", nullable = false, length = 50)
    private String moduleType;

    /**
     * Meta approved template name (e.g. crm_course_information, crm_fee_reminder_utility)
     */
    @Column(name = "template_name", nullable = false, length = 100)
    private String templateName;

    /**
     * Meta conversation billing category: UTILITY, MARKETING
     */
    @Column(name = "billing_category", length = 50)
    private String billingCategory;

    /**
     * Trigger mechanism: MANUAL (user clicked send), AUTOMATED_SCHEDULER (cron), TEST
     */
    @Column(name = "trigger_type", nullable = false, length = 50)
    private String triggerType;

    /**
     * Username of user who initiated the dispatch, or 'SYSTEM' for scheduler
     */
    @Column(name = "triggered_by_username", nullable = false, length = 100)
    private String triggeredByUsername;

    /**
     * Employee name or friendly sender display name
     */
    @Column(name = "triggered_by_employee_name", length = 150)
    private String triggeredByEmployeeName;

    /**
     * Primary entity ID (e.g. Enquiry.id or Admission.id)
     */
    @Column(name = "reference_id")
    private Long referenceId;

    /**
     * Human-readable reference number (e.g. Enquiry No 'ENQ-001' or Reg No 'TTS/2026/001')
     */
    @Column(name = "reference_number", length = 100)
    private String referenceNumber;

    /**
     * Recipient candidate or student name
     */
    @Column(name = "student_name", length = 150)
    private String studentName;

    /**
     * Destination phone number (normalized 10-digit or international format)
     */
    @Column(name = "recipient_mobile", nullable = false, length = 30)
    private String recipientMobile;

    /**
     * Course or package name associated with the message
     */
    @Column(name = "course_name", length = 255)
    private String courseName;

    /**
     * Attached brochure filename or zip file (for course brochures)
     */
    @Column(name = "attachment_name", length = 255)
    private String attachmentName;

    /**
     * Dispatch status: SUCCESS, FAILED, BLOCKED
     */
    @Column(name = "status", nullable = false, length = 30)
    private String status;

    /**
     * Unique Meta message ID (wamid.HBg...) returned by Meta Cloud API on delivery
     */
    @Column(name = "meta_message_id", length = 150)
    private String metaMessageId;

    /**
     * Description of failure reason if status != SUCCESS
     */
    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    /**
     * HTTP response status code (e.g. 200, 400, 403, 500)
     */
    @Column(name = "http_status_code")
    private Integer httpStatusCode;

    /**
     * Raw JSON request payload sent to Meta Cloud API
     */
    @Lob
    @Column(name = "request_payload", columnDefinition = "TEXT")
    private String requestPayload;

    /**
     * Raw JSON response payload returned by Meta Cloud API
     */
    @Lob
    @Column(name = "response_payload", columnDefinition = "TEXT")
    private String responsePayload;

    /**
     * Exact timestamp when the API call was processed
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
}
