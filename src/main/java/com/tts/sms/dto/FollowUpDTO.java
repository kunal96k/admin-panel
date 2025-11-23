package com.tts.sms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FollowUpDTO {

    private Long id;
    private Long enquiryId;

    private LocalDate followUpDate;
    private LocalDate nextFollowUpDate;

    private String mode;
    private String note;

    private LocalDateTime createdAt;

}