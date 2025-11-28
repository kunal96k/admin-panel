// FeeReceiptRequestDTO.java
package com.tts.sms.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeeReceiptRequestDTO {

    @NotNull(message = "Registration number is required")
    private String regNo;

    private Long installmentId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate receiptDate;

    @NotNull(message = "Amount received is required")
    private Double amountReceived;

    private Double previousPaid;

    private Double totalFees;

    private Double pendingFees;

    // GST Fields
    private Boolean gstEnabled;

    private Double sgstPercent;

    private Double cgstPercent;

    private Double invoiceValue;

    // Payment Details
    @NotNull(message = "Payment mode is required")
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

    private String receiptType;

    private String notes;
}
