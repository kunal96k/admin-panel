package com.tts.sms.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Response DTO for enquiry data
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnquiryResponseDTO {
    private Long id;
    private String name;
    private String firstName;
    private String middleName;
    private String lastName;
    private String mobile;
    private String secondaryMobile;
    private String email;
    private String secondaryEmail;
    private String currentAddress;
    private String permanentAddress;
    private String pinCurrent;
    private String pinPermanent;
    private String college;
    private String qualification;
    private String aadhaar;
    private LocalDate birthDate;
    private String gender;
    private String courses;
    private List<String> coursesList;
    private String packageName;
    private Boolean demoLectureRequired;
    private String interestLevel;
    private String source;
    private String referenceName;
    private LocalDate date;
    private LocalDate followupDate;
    private String assign;
    private String status;
    private String note;
    private String importSource;
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime updatedAt;
}