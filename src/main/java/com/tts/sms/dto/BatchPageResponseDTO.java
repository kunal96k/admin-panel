package com.tts.sms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchPageResponseDTO {
    private List<BatchResponseDTO> batches;
    private long totalElements;
    private int totalPages;
    private int currentPage;
    private int pageSize;
}
