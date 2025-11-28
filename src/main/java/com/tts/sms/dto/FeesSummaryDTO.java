package com.tts.sms.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
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

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dueDate; // CAN BE NULL

    private String status; // Pending, Clear

    // Installment info
    private Integer totalInstallments;

    private Integer paidInstallments;

    private Integer pendingInstallments;

    // Computed properties
    public Boolean isOverdue() {
        if (dueDate == null || !"Pending".equals(status)) {
            return false;
        }
        return dueDate.isBefore(LocalDate.now());
    }

    public Double getPaymentPercentage() {
        if (totalFees == null || totalFees == 0) {
            return 0.0;
        }
        return (totalPaid / totalFees) * 100;
    }
}