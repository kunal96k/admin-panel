package com.tts.sms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeeInstallmentBatchDTO {

    @NotNull(message = "Registration number is required")
    private String registrationNumber;

    @NotEmpty(message = "At least one installment is required")
    @Valid
    private List<FeeInstallmentCreateDTO> installments;
}