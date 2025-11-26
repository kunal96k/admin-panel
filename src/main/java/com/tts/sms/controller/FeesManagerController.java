package com.tts.sms.controller;

import com.tts.sms.dto.*;
import com.tts.sms.service.FeesManagerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/fees-manager")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class FeesManagerController {

    private final FeesManagerService feesManagerService;

    // ==================== FEES SUMMARY ====================

    /**
     * Get all fees summary with pagination
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Page<FeesSummaryDTO>> getAllFees(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        log.info("GET /api/fees-manager - page: {}, size: {}", page, size);

        Page<FeesSummaryDTO> fees = feesManagerService.getAllFeesSummary(page, size);
        return ResponseEntity.ok(fees);
    }

    /**
     * Search fees with filters
     */
    @PostMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Page<FeesSummaryDTO>> searchFees(
            @RequestBody FeesSearchDTO searchDTO) {

        log.info("POST /api/fees-manager/search - {}", searchDTO);

        Page<FeesSummaryDTO> results = feesManagerService.searchFees(searchDTO);
        return ResponseEntity.ok(results);
    }

    // ==================== FEE RECEIPTS ====================

    /**
     * Create new fee receipt
     */
    @PostMapping(value = "/receipts", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FeeReceiptResponseDTO> createFeeReceipt(
            @Valid @RequestBody FeeReceiptRequestDTO requestDTO) {

        log.info("POST /api/fees-manager/receipts - admission: {}", requestDTO.getAdmissionId());

        FeeReceiptResponseDTO receipt = feesManagerService.createFeeReceipt(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(receipt);
    }

    /**
     * Get receipts by admission ID
     */
    @GetMapping(value = "/receipts/admission/{admissionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<FeeReceiptResponseDTO>> getReceiptsByAdmission(
            @PathVariable Long admissionId) {

        log.info("GET /api/fees-manager/receipts/admission/{}", admissionId);

        List<FeeReceiptResponseDTO> receipts = feesManagerService.getReceiptsByAdmission(admissionId);
        return ResponseEntity.ok(receipts);
    }

    /**
     * Delete fee receipt (soft delete)
     */
    @DeleteMapping(value = "/receipts/{receiptId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> deleteFeeReceipt(@PathVariable Long receiptId) {
        log.info("DELETE /api/fees-manager/receipts/{}", receiptId);

        feesManagerService.deleteFeeReceipt(receiptId);
        return ResponseEntity.noContent().build();
    }

    // ==================== FEE REFUNDS ====================

    /**
     * Create new fee refund
     */
    @PostMapping(value = "/refunds", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FeeRefundResponseDTO> createFeeRefund(
            @Valid @RequestBody FeeRefundRequestDTO requestDTO) {

        log.info("POST /api/fees-manager/refunds - admission: {}", requestDTO.getAdmissionId());

        FeeRefundResponseDTO refund = feesManagerService.createFeeRefund(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(refund);
    }

    /**
     * Get refunds by admission ID
     */
    @GetMapping(value = "/refunds/admission/{admissionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<FeeRefundResponseDTO>> getRefundsByAdmission(
            @PathVariable Long admissionId) {

        log.info("GET /api/fees-manager/refunds/admission/{}", admissionId);

        List<FeeRefundResponseDTO> refunds = feesManagerService.getRefundsByAdmission(admissionId);
        return ResponseEntity.ok(refunds);
    }

    // ==================== STATUS UPDATE ====================

    /**
     * Update fee payment status
     */
    @PutMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> updateFeeStatus(
            @Valid @RequestBody FeeStatusUpdateDTO updateDTO) {

        log.info("PUT /api/fees-manager/status - admission: {}, status: {}",
                updateDTO.getAdmissionId(), updateDTO.getPaymentStatus());

        feesManagerService.updateFeeStatus(updateDTO);

        return ResponseEntity.ok(Map.of(
                "success", "true",
                "message", "Fee status updated successfully"
        ));
    }

    // ==================== CSV IMPORT ====================

    /**
     * Bulk import fees data from CSV
     */
    @PostMapping(value = "/bulk-import", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FeesBulkImportResponseDTO> bulkImportFromCSV(
            @RequestParam("file") MultipartFile file) {

        log.info("POST /api/fees-manager/bulk-import - file: {}", file.getOriginalFilename());

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(FeesBulkImportResponseDTO.builder()
                            .success(false)
                            .message("File is empty")
                            .build());
        }

        FeesBulkImportResponseDTO result = feesManagerService.bulkImportFeesCSV(file);

        HttpStatus status = result.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(result);
    }

    /**
     * Bulk import fees from JSON
     */
    @PostMapping(value = "/bulk-import-json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FeesBulkImportResponseDTO> bulkImportFromJSON(
            @RequestBody List<FeesCSVImportDTO> fees) {

        log.info("POST /api/fees-manager/bulk-import-json - {} records", fees.size());

        if (fees == null || fees.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(FeesBulkImportResponseDTO.builder()
                            .success(false)
                            .totalRecords(0)
                            .successfulImports(0)
                            .failedImports(0)
                            .message("Fees list cannot be empty")
                            .build());
        }

        FeesBulkImportResponseDTO result = feesManagerService.processBulkFeesImport(fees);

        HttpStatus status = result.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(result);
    }

    // ==================== EXCEPTION HANDLING ====================

    /**
     * Exception handler for validation errors
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleValidationErrors(
            IllegalArgumentException ex) {

        log.error("Validation error: {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "success", "false",
                        "message", ex.getMessage()
                ));
    }
}