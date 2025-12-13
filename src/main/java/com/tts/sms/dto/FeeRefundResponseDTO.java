package com.tts.sms.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeeRefundResponseDTO {
    private Long id;
    private String refundNumber;

    // CHANGED: Use registrationNumber as primary identifier
    private String registrationNumber;
    private String studentName;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate refundDate;

    private Double refundAmount;
    private Double totalFees;
    private Double paidFees;
    private Double pendingFees;

    // Payment Details
    private String paymentMode;
    private String bankName;
    private String chequeNumber;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate chequeDate;

    private String transactionNumber;
    private String ifscCode;
    private String onlinePaymentMode;
    private Boolean paymentClear;

    private String notes;
    private String status;

    @Schema(description = "User who issued the refund")
    private String issuedBy;

    @Schema(description = "Created by username")
    private String createdBy;
}