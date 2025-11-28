package com.tts.sms.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
class CourseUpdateDTO {
    private Long id;
    private String courseName;
    private BigDecimal courseFees;
}
