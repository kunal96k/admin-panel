package com.tts.sms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CaptchaVerificationRequest {

    @NotBlank(message = "Username is required")
    private String username;

    @NotNull(message = "CAPTCHA answer is required")
    private String captchaAnswer;

    @NotBlank(message = "CAPTCHA token is required")
    private String captchaToken;
}