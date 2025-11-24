package com.tts.sms.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * DTO for Admission Search with filters
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdmissionSearchDTO {

    private String searchTerm;  // Search in name, mobile, reg no

    private String status;

    private String course;

    private String batch;

    private String academicYear;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate admissionDateFrom;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate admissionDateTo;

    @Builder.Default
    private Integer page = 0;

    @Builder.Default
    private Integer size = 25;

    @Builder.Default
    private String sortBy = "admissionDate";

    @Builder.Default
    private String sortDirection = "DESC";
}
