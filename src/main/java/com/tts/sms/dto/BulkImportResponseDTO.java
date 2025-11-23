package com.tts.sms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for bulk import results
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkImportResponseDTO {
    private boolean success;
    private int totalRecords;
    private int successfulImports;
    private int failedImports;
    private String message;
    private List<ImportError> errors;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ImportError {
        private int rowNumber;
        private String fieldName;
        private String errorMessage;
        private String rejectedValue;
    }
}