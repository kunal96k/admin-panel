package com.tts.sms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CertificateDTO {

    private Long id;
    private String registrationNo;
    private String certificateNo;
    private String studentName;
    private String studentEmail;
    private String courseName;
    private String batch;
    private String grade;
    private LocalDate issueDate;
    private LocalDate courseFromDate;
    private LocalDate courseToDate;
    private String status;
    private String notes;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String courseImagePath;
}