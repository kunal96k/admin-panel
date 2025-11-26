package com.tts.sms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DTO for searching fees with multiple criteria
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeesSearchDTO {

    // Pagination
    @Builder.Default
    private int page = 0;

    @Builder.Default
    private int size = 25;

    @Builder.Default
    private String sortBy = "createdAt";

    @Builder.Default
    private String sortDirection = "DESC";

    private String searchTerm;

    private String status; // Pending, Clear

    private String course;

    private LocalDate dueDateFrom;

    private LocalDate dueDateTo;

    private Double minTotalFees;

    private Double maxTotalFees;

    private Double minFeesDue;

    private Double maxFeesDue;

    private Boolean overdue;
}