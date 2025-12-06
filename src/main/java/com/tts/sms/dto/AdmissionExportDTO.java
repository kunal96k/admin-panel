package com.tts.sms.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdmissionExportDTO {
    private String registrationNumber;
    private String studentName;
    private String mobile;
    private String email;
    private String courses;
    private String college;
    private String totalFees;      // String to show "-" if null
    private String receivableFees; // String to show "-" if null
    private String admissionDate;
}