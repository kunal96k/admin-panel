package com.tts.sms.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubjectDropdownDTO {
    private Long id;
    private String subjectName;
    private Long courseId;
}