package com.tts.sms.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
class CourseSearchDTO {
    private String searchTerm;
    private Boolean isActive;
    private Integer page;
    private Integer size;
}