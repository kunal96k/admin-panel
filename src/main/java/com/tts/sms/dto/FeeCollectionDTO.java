package com.tts.sms.dto;

import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeeCollectionDTO {
    private Long id;
    private String receiptNo;
    private String studentName;
    private String mobileNo;
    private LocalDate receiptDate;
    private String receiptDateOriginal;
    private Double paidFees;
    private String paymentMode;
    private String dataSource;
    private String registrationNumber;
    private String importBatchId;
    private String notes;
    private LocalDateTime createdAt;
}