package com.tts.sms.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeeReceiptResponseDTO {
    private Long id;
    private String receiptNumber;
    private String invoiceNumber;
    private String registrationNumber;
    private String studentName;

    private Long installmentId;

    @JsonFormat(pattern = "yyyy-MM-dd")
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

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate chequeDate;

    private String transactionNumber;
    private String ifscCode;
    private String onlinePaymentMode;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate nextDueDate;

    private Double currentPendingFees;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate currentNextDueDate;

    /**
     * Data source marker: "IMPORTED_OLD_DATA" or "NEW_ENTRY"
     */
    private String dataSource;

    private String course;

    private String receiptType;
    private String notes;
    private String status;
    private String mobile;

}