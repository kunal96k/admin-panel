package com.tts.sms.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeResponseDTO {

    private Long id;
    private String employeeName;
    private String mobileNumber;
    private String emailId;
    private String designation;
    private String gender;
    private String dateOfBirth;
    private String address;
    private Long roleId;
    private String roleName;
    private String zoomLink;
    private String photoFilename;
    private String photoUrl;
    private Boolean isTrainer;
    private Boolean applyBatchFilter;
    private Boolean isAdmin;
    private Boolean isActive;
    private Boolean userActive;
    private Boolean userLocked;
    private Integer failedAttempts;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdDate;
}