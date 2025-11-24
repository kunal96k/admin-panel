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

    // Contact Information
    @NotBlank(message = "Primary mobile is required")
    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Invalid mobile number")
    private String mobilePrimary;

    @Pattern(regexp = "^$|^[6-9]\\d{9}$", message = "Invalid secondary mobile number")
    private String mobileSecondary;

    @Email(message = "Invalid email format")
    private String emailPrimary;

    @Email(message = "Invalid secondary email format")
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