package com.tts.sms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PackageDTO {
    private Long id;
    private String packageName;
    private BigDecimal totalAmount;
    private Boolean isActive;
    private List<CourseDTO> courses;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}