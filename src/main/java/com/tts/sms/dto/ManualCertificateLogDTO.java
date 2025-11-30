package com.tts.sms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualCertificateLogDTO {
    private Long id;
    private String registrationNo;
    private String studentName;
    private String courseName;
    private String createdByEmployeeName;
    private String reason;
    private Long certificateId;
    private LocalDateTime createdAt;
}