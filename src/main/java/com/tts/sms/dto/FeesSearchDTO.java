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
public class FeesSearchDTO {
    private String searchTerm; // Search by reg no, name, mobile
    private String status; // Pending, Clear
    private String course;
    private LocalDate dueDateFrom;
    private LocalDate dueDateTo;

    @Builder.Default
    private int page = 0;

    @Builder.Default
    private int size = 25;

    @Builder.Default
    private String sortBy = "dueDate";

    @Builder.Default
    private String sortDirection = "ASC";
}
