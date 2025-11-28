package com.tts.sms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PackageCreateRequest {
    private String packageName;
    private List<Long> courseIds;
    private BigDecimal totalAmount;
    private List<CourseInfo> courses;
}