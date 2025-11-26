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
public class FeesCSVImportDTO {
    private String registrationNumber;
    private String studentName;
    private String mobile;
    private Double totalFees;
    private Double feesDue;
    private Double totalPaid;
    private LocalDate dueDate;
    private Double feesRefund;
    private String status;
    private String course;
}