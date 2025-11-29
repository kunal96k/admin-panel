package com.tts.sms.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchRequestDTO {

    @NotBlank(message = "Batch name is required")
    @Size(max = 100, message = "Batch name must not exceed 100 characters")
    private String batchName;

    @NotNull(message = "Batch size is required")
    @Min(value = 1, message = "Batch size must be at least 1")
    @Max(value = 200, message = "Batch size must not exceed 200")
    private Integer batchSize;

    private LocalDate startDate;

    private LocalDate endDate;

    @NotNull(message = "Start time is required")
    private LocalTime startTime;

    @NotNull(message = "End time is required")
    private LocalTime endTime;

    private Boolean isSunday = false;
    private Boolean isMonday = false;
    private Boolean isTuesday = false;
    private Boolean isWednesday = false;
    private Boolean isThursday = false;
    private Boolean isFriday = false;
    private Boolean isSaturday = false;

    private Long courseId;

    private String status;
}