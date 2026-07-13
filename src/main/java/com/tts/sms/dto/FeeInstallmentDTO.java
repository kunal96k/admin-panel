package com.tts.sms.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeeInstallmentDTO {

    private Long id;

    private String registrationNumber;

    private Integer installmentNumber;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dueDate;

    private Double amount;

    private Double paidAmount;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate paidDate;

    private String paymentMode;

    private String transactionId;

    private String status; // Pending, Paid, Overdue, Waived

    private String notes;

    private Boolean isOverdue;

    private String createdBy;

    private String updatedBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private java.time.LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private java.time.LocalDateTime updatedAt;

    // Helper method to check if overdue
    public Boolean getIsOverdue() {
        if ("Pending".equals(status) && dueDate != null) {
            return dueDate.isBefore(LocalDate.now());
        }
        return false;
    }
}
