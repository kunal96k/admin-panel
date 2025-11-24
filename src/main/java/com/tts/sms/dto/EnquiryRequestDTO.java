package com.tts.sms.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnquiryRequestDTO {

    // **NEW: Enquiry Number from CSV**
    private String enquiryNo;

    // Name fields
    private String name;
    private String firstName;
    private String middleName;
    private String lastName;

    // Contact
    @NotBlank(message = "Mobile number is required")
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

    // Course
    @NotNull(message = "At least one course is required")
    @Size(min = 1)
    private List<String> courses;

    private String packageName;
    private Boolean demoLectureRequired;
    private String interestLevel;

    @NotBlank(message = "Source is required")
    private String source;

    private String referenceName;
    private LocalDate enquiryDate;
    private LocalDate followupDate;
    private String assignTo;
    private String status;
    private String note;
}