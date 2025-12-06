package com.tts.sms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchDropdownDTO {
    private Long id;
    private String batchNo;
    private String batchName;
    private LocalTime startTime;
    private LocalTime endTime;
}