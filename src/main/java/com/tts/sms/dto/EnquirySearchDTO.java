package com.tts.sms.dto;


import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DTO for search/filter operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnquirySearchDTO {

    private String searchTerm;
    private String status;
    private String source;
    private String course;
    private String assignTo;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fromDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate toDate;

    @Builder.Default
    private Integer page = 0;

    @Builder.Default
    private Integer size = 25;

    @Builder.Default
    private String sortBy = "enquiryDate";

    @Builder.Default
    private String sortDirection = "DESC";
}