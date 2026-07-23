package com.tts.sms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceStudentSyncDTO {
    private String regNo;
    private String fullName;
    private String email;
    private String mobileNo;
    private List<String> courses;
    private String courseName;
    private LocalDate admissionDate;
}
