package com.tts.sms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OnlinePaymentModeDTO {

    private Long id;

    @NotBlank(message = "Payment mode title is required")
    @Size(min = 2, max = 500, message = "Payment mode title must be between 2 and 500 characters")
    private String paymentModeTitle;

    private Boolean isActive;

    private LocalDateTime createdDate;

    private LocalDateTime updatedDate;
}