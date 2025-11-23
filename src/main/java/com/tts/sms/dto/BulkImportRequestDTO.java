package com.tts.sms.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for bulk import operations
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkImportRequestDTO {

    @NotNull(message = "Import type is required")
    private ImportType importType;

    @NotEmpty(message = "Enquiries list cannot be empty")
    private List<EnquiryRequestDTO> enquiries;

    public enum ImportType {
        OLD_FORMAT,
        NEW_FORMAT
    }
}