package com.tts.sms.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferAdmissionDTO {

    @NotNull(message = "Admission ID is required")
    private Long admissionId;

    @NotNull(message = "Academic year is required")
    private String academicYear;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate transferDate;

    private List<String> courses;

    private List<String> batches;

    private List<String> subjects;

    private String packageName;

    private Double totalPayableFees;

    private Double totalReceivableFees;

    private Double discountPercent;

    private Double discountAmount;

    private String transferReason;
}