package com.tts.sms.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdmissionResponseDTO {

    private Long id;
    private String registrationNumber;
    private String rollNumber;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate admissionDate;
    private Long enquiryId;

    // Personal Information
    private String studentName;
    private String firstName;
    private String middleName;
    private String lastName;
    private String college;
    private String qualification;
    private String aadhaar;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate birthDate;
    private String gender;
    private String cast;
    private String category;
    private String physicallyHandicapped;
    private String bloodGroup;

    // Contact Information
    private String mobilePrimary;
    private String mobileSecondary;
    private String emailPrimary;
    private String emailSecondary;
    private String currentAddress;
    private String permanentAddress;
    private String pinCodeCurrent;
    private String pinCodePermanent;

    // Course Details
    private String packageName;
    private String courses; // Comma-separated string
    private List<String> coursesList; // Parsed list
    private String courseFeesDetails;
    private Double totalPayableFees;
    private Double totalReceivableFees;
    private Double discountPercent;
    private Double discountAmount;

    // Batch Details
    private String batches; // Comma-separated string
    private List<String> batchesList; // Parsed list
    private List<BatchResponseDTO> batchDetails; // Full batch details
    private String subjects; // Comma-separated string
    private List<String> subjectsList; // Parsed list
    private String academicYear;

    // Other Details
    private String documentType;
    private String leadSource;
    private String notes;
    private String photoPath;
    private String status;

    // Fee Installments
    private List<FeeInstallmentDTO> installments;
    private Integer totalInstallments;
    private Double totalPaidAmount;
    private Double totalDueAmount;

    // Metadata
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate installmentStartDate;
    private Integer numberOfInstallments;
    private Integer daysBetweenInstallments;
    private Double totalInstallmentAmount;

    private String studentCategory;
    private LocalDateTime categoryUpdatedAt;
    private String feesStatus;

    private String createdBy;
    private String updatedBy;
    private Boolean feeReminderEmailEnabled;
}