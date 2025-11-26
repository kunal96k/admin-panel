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
public class FeeReceiptResponseDTO {
    private Long id;
    private String receiptNumber;
    private String invoiceNumber;
    private Long admissionId;
    private String studentName;
    private String registrationNumber;
    private Long installmentId;
    private LocalDate receiptDate;
    private Double amountReceived;
    private Double previousPaid;
    private Double totalFees;
    private Double pendingFees;

    // GST
    private Boolean gstEnabled;
    private Double sgstPercent;
    private Double cgstPercent;
    private Double invoiceValue;

    // Payment Details
    private String paymentMode;
    private String bankName;
    private String chequeNumber;
    private LocalDate chequeDate;
    private String transactionNumber;
    private String ifscCode;
    private String onlinePaymentMode;

    private LocalDate nextDueDate;
    private String receiptType;
    private String notes;
    private String status;
}