package com.tts.sms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdmissionCursorResponseDTO {
    private List<AdmissionResponseDTO> content;
    private Long nextCursor;
    private boolean hasMore;
    private long totalElements; // Still useful for UI info
}
