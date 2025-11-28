package com.tts.sms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeadSourceDTO {

    private Long id;

    @NotBlank(message = "Lead source title is required")
    @Size(max = 50, message = "Lead source title must not exceed 50 characters")
    private String sourceTitle;

    private Boolean isActive;
}