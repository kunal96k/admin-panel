package com.tts.sms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeeCollectionSearchDTO {
    private LocalDate fromDate;
    private LocalDate toDate;
    private String paymentMode;
    private String dataSource; // IMPORTED_OLD_DATA or NEW_ENTRY
    private int page = 0;
    private int size = 25;
}
