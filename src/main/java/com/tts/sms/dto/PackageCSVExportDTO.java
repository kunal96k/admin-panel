package com.tts.sms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PackageCSVExportDTO {
    private Integer serialNo;
    private String packageName;
    private String courseNames;
    private BigDecimal totalAmount;
}