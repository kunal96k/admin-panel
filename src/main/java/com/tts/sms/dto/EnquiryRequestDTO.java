package com.tts.sms.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;
import java.util.List;

/**
 * Request DTO for creating/updating enquiries
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnquiryRequestDTO {

    // Old format support - single name field
    private String name;

    // New format - separate name fields
    private String firstName;
    private String middleName;
    private String lastName;

    // Contact Information
    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Invalid mobile number")
    private String mobile;

    @Pattern(regexp = "^$|^[6-9]\\d{9}$", message = "Invalid secondary mobile")
    private String secondaryMobile;

    @Email(message = "Invalid email format")
    private String email;

    @Email(message = "Invalid secondary email format")
    private String secondaryEmail;

    // Address
    private String currentAddress;
    private String permanentAddress;

    @Size(max = 10, message = "Pin code must not exceed 10 characters")
    private String pinCurrent;

    @Size(max = 10, message = "Pin code must not exceed 10 characters")
    private String pinPermanent;

    // Academic
    @Size(max = 200, message = "College name must not exceed 200 characters")
    private String college;

    @Size(max = 100, message = "Qualification must not exceed 100 characters")
    private String qualification;

    @Pattern(regexp = "^$|^\\d{12}$", message = "Aadhaar must be 12 digits")
    private String aadhaar;

    private LocalDate birthDate;

    private String gender;

    // Course Interest
    @NotNull(message = "At least one course is required")
    @Size(min = 1, message = "At least one course is required")
    private List<String> courses;

    private String packageName;
    private Boolean demoLectureRequired;
    private String interestLevel;

    // Enquiry Details
    @NotBlank(message = "Source is required")
    private String source;

    private String referenceName;

    @NotNull(message = "Enquiry date is required")
    private LocalDate enquiryDate;

    private LocalDate followupDate;
    private String assignTo;
    private String status;
    private String note;
}
