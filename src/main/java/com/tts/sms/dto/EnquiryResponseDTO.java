package com.tts.sms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnquiryResponseDTO {

    private Long id;

    // **NEW: Enquiry Number for display**
    private String enquiryNo;

    // Name fields
    private String name;
    private String firstName;
    private String middleName;
    private String lastName;

    // Contact
    private String mobile;
    private String secondaryMobile;
    private String email;
    private String secondaryEmail;

    // Address
    private String currentAddress;
    private String permanentAddress;
    private String pinCurrent;
    private String pinPermanent;

    // Academic
    private String college;
    private String qualification;
    private String aadhaar;
    private LocalDate birthDate;
    private String gender;

    // Course - Both string and list format for flexibility
    private String courses;  // Comma-separated string
    private List<String> coursesList;  // List format

    private String packageName;
    private Boolean demoLectureRequired;
    private String interestLevel;

    // Enquiry details
    private String source;
    private String referenceName;
    private LocalDate date;  // enquiryDate - ALIAS FIELD
    private LocalDate enquiryDate;
    private LocalDate followupDate;
    private String assign;  // assignTo
    private String status;
    private String note;
    private Double totalFees;

    // Metadata
    private String importSource;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String createdBy;
    private String updatedBy;
}