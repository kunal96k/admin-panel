package com.tts.sms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceSyncResponseDTO {
    private boolean success;
    private String message;
    private Integer processedCount;
    private String timestamp;
}
