package com.tts.sms.dto;

import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeesBulkImportResponseDTO {
    private boolean success;
    private int totalRecords;
    private int successfulImports;
    private int failedImports;
    private String message;

    @Builder.Default
    private List<ImportError> errors = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportError {
        private int rowNumber;
        private String fieldName;
        private String errorMessage;
        private String rejectedValue;
    }
}