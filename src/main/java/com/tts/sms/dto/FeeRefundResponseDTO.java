package com.tts.sms.dto;

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
    private Long admissionId;
    private String studentName;
    private String registrationNumber;
    private LocalDate refundDate;
    private Double refundAmount;
    private Double totalFees;
    private Double paidFees;
    private Double pendingFees;

    // Payment Details
    private String paymentMode;
    private String bankName;
    private String chequeNumber;
    private LocalDate chequeDate;
    private String transactionNumber;
    private String ifscCode;
    private String onlinePaymentMode;

    private Boolean paymentClear;
    private String notes;
    private String status;
}
