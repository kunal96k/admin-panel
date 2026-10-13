package com.tts.sms.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Lightweight summary DTO for high-performance paginated rendering of WhatsApp audit logs.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WhatsAppAuditLogSummaryDTO {

    private Long id;
    private String moduleType;
    private String templateName;
    private String billingCategory;
    private String triggerType;
    private String triggeredByUsername;
    private String triggeredByEmployeeName;
    private Long referenceId;
    private String referenceNumber;
    private String studentName;
    private String recipientMobile;
    private String courseName;
    private String attachmentName;
    private String status;
    private String metaMessageId;
    private String failureReason;
    private Integer httpStatusCode;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
}
