package com.tts.sms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for updating fee payment status
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeeStatusUpdateDTO {

    @NotNull(message = "Admission ID is required")
    private Long admissionId;

    @NotBlank(message = "Payment status is required")
    private String paymentStatus;

    private String notes;
}