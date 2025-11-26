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
public class FeesSummaryDTO {
    private Long admissionId;
    private String registrationNumber;
    private String studentName;
    private String mobile;
    private String course;

    private Double totalFees;
    private Double totalPaid;
    private Double feesDue;
    private Double feesRefund;

    private LocalDate dueDate;
    private String status; // Pending, Clear

    private Integer totalInstallments;
    private Integer paidInstallments;
    private Integer pendingInstallments;
}

