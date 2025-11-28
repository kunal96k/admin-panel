package com.tts.sms.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeeStatusUpdateDTO {

    @NotBlank(message = "Registration number is required")
    private String registrationNumber;

    @NotBlank(message = "Payment status is required")
    private String paymentStatus;
}