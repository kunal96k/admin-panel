package com.tts.sms.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
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
public class FeeRefundRequestDTO {

    @NotBlank(message = "Registration number is required")
    private String regNo;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate refundDate;

    @NotNull(message = "Refund amount is required")
    private Double refundAmount;

    private Double totalFees;

    private Double paidFees;

    private Double pendingFees;

    // Payment Details
    @NotBlank(message = "Payment mode is required")
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
}
