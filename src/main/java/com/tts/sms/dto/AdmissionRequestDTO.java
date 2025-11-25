package com.tts.sms.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdmissionRequestDTO {

    private Long enquiryId;

    // Personal Information
    @NotBlank(message = "First name is required")
    private String firstName;

    private String middleName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    private String college;
    private String qualification;

    @Pattern(regexp = "^$|^\\d{12}$", message = "Aadhaar must be 12 digits")
    private String aadhaar;

    private LocalDate birthDate;
    private String gender;
    private String cast;
    private String category;
    private String physicallyHandicapped;
    private String bloodGroup;

    private String registrationNumber;

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
    private List<String> courses;
    private Double totalPayableFees;
    private Double totalReceivableFees;
    private Double discountPercent;
    private Double discountAmount;

    // Batch Details
    private List<String> batches;
    private List<String> subjects;
    private String academicYear;

    // Other Details
    private String documentType;
    private String leadSource;
    private LocalDate admissionDate;
    private String rollNumber;
    private String notes;

    // Installment Configuration
    private InstallmentConfigDTO installmentConfig;
}