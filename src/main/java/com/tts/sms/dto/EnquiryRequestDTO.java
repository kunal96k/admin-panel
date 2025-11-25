package com.tts.sms.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.tts.sms.config.CustomLocalDateDeserializer;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnquiryRequestDTO {

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

    @JsonDeserialize(using = CustomLocalDateDeserializer.class)
    private LocalDate birthDate;
    private String gender;

    // Course
    @Size(min = 1)
    private List<String> courses;

    private String packageName;
    private Boolean demoLectureRequired;
    private String interestLevel;


    private String source;

    private String referenceName;

    @JsonDeserialize(using = CustomLocalDateDeserializer.class)
    private LocalDate enquiryDate;

    @JsonDeserialize(using = CustomLocalDateDeserializer.class)
    private LocalDate followupDate;
    private String assignTo;
    private String status;
    private String note;
}