package com.tts.sms.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeeInstallmentUpdateDTO {

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dueDate;

    @Positive(message = "Amount must be positive")
    private Double amount;
}