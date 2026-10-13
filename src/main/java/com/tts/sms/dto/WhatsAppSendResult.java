package com.tts.sms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhatsAppSendResult {
    private boolean success;
    private String messageId;
    private String message;
    private String recipientMobile;

    public static WhatsAppSendResult success(String messageId, String recipientMobile) {
        return WhatsAppSendResult.builder()
                .success(true)
                .messageId(messageId)
                .recipientMobile(recipientMobile)
                .message("WhatsApp message sent successfully")
                .build();
    }

    public static WhatsAppSendResult failure(String errorMessage, String recipientMobile) {
        return WhatsAppSendResult.builder()
                .success(false)
                .recipientMobile(recipientMobile)
                .message(errorMessage)
                .build();
    }
}
