package com.tts.sms.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeeInstallmentCreateDTO {

    @NotNull
    private Integer installmentNumber;

    @NotNull
    private LocalDate dueDate;

    @NotNull
    @Positive
    private Double amount;

    @NotNull
    private String status;
}