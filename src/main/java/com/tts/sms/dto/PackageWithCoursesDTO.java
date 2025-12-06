package com.tts.sms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PackageWithCoursesDTO {
    private Long id;
    private String packageName;
    private BigDecimal totalAmount;
    private List<CourseDropdownDTO> courses;
}