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
public class CertificateCSVImportDTO {

    // Required fields
    private String registrationNo;
    private String studentName;
    private String studentEmail;
    private String courseName;

    private String certificateNo;
    private String mobile;
    private String batch;
    private String grade;
    private LocalDate issueDate;
    private LocalDate admissionDate;
    private String status;
}