package com.tts.sms.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InstallmentConfigDTO {

    @NotNull(message = "Start date is required")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @NotNull(message = "Number of installments is required")
    @Min(value = 1, message = "At least 1 installment is required")
    private Integer numberOfInstallments;

    @NotNull(message = "Days between installments is required")
    @Min(value = 1, message = "Days between installments must be at least 1")
    private Integer daysBetween;
}
