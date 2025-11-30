package com.tts.sms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for manual certificate generation request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualCertificateRequestDTO {
    private String registrationNo;
    private String studentName;
    private String courseName;
    private String reason;
}