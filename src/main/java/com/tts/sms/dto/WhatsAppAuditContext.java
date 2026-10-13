package com.tts.sms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Context container carrying entity and caller metadata for WhatsApp audit logging.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WhatsAppAuditContext {

    /**
     * Functional module: COURSE_INFORMATION, FEE_REMINDER, TEST
     */
    private String moduleType;

    /**
     * Meta template name: crm_course_information, crm_fee_reminder_utility
     */
    private String templateName;

    /**
     * Meta billing category: UTILITY, MARKETING
     */
    private String billingCategory;

    /**
     * Trigger source: MANUAL, AUTOMATED_SCHEDULER, TEST
     */
    @Builder.Default
    private String triggerType = "MANUAL";

    /**
     * Username who initiated the action (e.g. aditya or SYSTEM)
     */
    private String triggeredByUsername;

    /**
     * Full employee name or friendly name (e.g. Aditya Ghogare or System Scheduler)
     */
    private String triggeredByEmployeeName;

    /**
     * Primary entity ID (e.g. Enquiry.id or Admission.id)
     */
    private Long referenceId;

    /**
     * Human-readable reference number (e.g. Enquiry No or Registration No)
     */
    private String referenceNumber;

    /**
     * Recipient candidate or student full name
     */
    private String studentName;

    /**
     * Raw or normalized recipient mobile number
     */
    private String recipientMobile;

    /**
     * Associated course or package name
     */
    private String courseName;

    /**
     * Attached document filename (PDF / ZIP)
     */
    private String attachmentName;
}
