package com.tts.sms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeeCollectionStatsDTO {
    private Long totalReceipts;
    private Double totalAmount;
    private Long oldDataCount;
    private Long newDataCount;
}
