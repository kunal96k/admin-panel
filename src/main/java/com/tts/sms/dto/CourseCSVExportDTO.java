package com.tts.sms.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseCSVExportDTO {
    private Integer srNo;
    private String courseName;
    private BigDecimal courseFees;
}