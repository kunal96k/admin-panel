package com.tts.sms.dto;

import lombok.*;
        import java.time.LocalDate;
import java.util.List;

// ==================== FEE RECEIPT DTOs ====================

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeeReceiptRequestDTO {
    private Long admissionId;
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
}