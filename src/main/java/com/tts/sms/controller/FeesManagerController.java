package com.tts.sms.controller;

import com.tts.sms.dto.*;
import com.tts.sms.exception.ResourceNotFoundException;
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

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/fees-manager")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class FeesManagerController {

    private final FeesManagerService feesManagerService;

    // ==================== FEES SUMMARY (MISSING ENDPOINT) ====================

    @PutMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> updateStatus(
            @Valid @RequestBody FeeStatusUpdateDTO statusDTO) {

        log.info("PUT /api/fees-manager/status - regNo: {}, status: {}",
                statusDTO.getRegistrationNumber(), statusDTO.getPaymentStatus());

        try {
            feesManagerService.updateFeeStatus(
                    statusDTO.getRegistrationNumber(),
                    statusDTO.getPaymentStatus()
            );

            return ResponseEntity.ok(Map.of(
                    "success", "true",
                    "message", "Fee status updated successfully"
            ));
        } catch (Exception e) {
            log.error("Error updating fee status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", "false",
                            "message", e.getMessage()
                    ));
        }
    }

    /**
     * Delete fee installment
     */
    @DeleteMapping(value = "/installments/{installmentId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> deleteFeeInstallment(@PathVariable Long installmentId) {
        log.info("DELETE /api/fees-manager/installments/{}", installmentId);

        try {
            feesManagerService.deleteFeeInstallment(installmentId);
            return ResponseEntity.ok(Map.of(
                    "success", "true",
                    "message", "Installment deleted successfully"
            ));
        } catch (Exception e) {
            log.error("Error deleting installment", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", "false",
                            "message", e.getMessage()
                    ));
        }
    }

    /**
     * Update existing fee receipt
     */
    @PutMapping(value = "/receipts/{receiptId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FeeReceiptResponseDTO> updateFeeReceipt(
            @PathVariable Long receiptId,
            @Valid @RequestBody FeeReceiptRequestDTO requestDTO) {

        log.info("PUT /api/fees-manager/receipts/{} - Updating receipt", receiptId);

        FeeReceiptResponseDTO receipt = feesManagerService.updateFeeReceipt(receiptId, requestDTO);
        return ResponseEntity.ok(receipt);
    }

    /**
     * Update fee status
     */
    @PutMapping(value = "/update-status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> updateFeeStatus(
            @Valid @RequestBody FeeStatusUpdateDTO statusDTO) {

        log.info("PUT /api/fees-manager/update-status - regNo: {}, status: {}",
                statusDTO.getRegistrationNumber(), statusDTO.getPaymentStatus());

        try {
            feesManagerService.updateFeeStatus(
                    statusDTO.getRegistrationNumber(),
                    statusDTO.getPaymentStatus()
            );

            return ResponseEntity.ok(Map.of(
                    "success", "true",
                    "message", "Fee status updated successfully"
            ));
        } catch (Exception e) {
            log.error("Error updating fee status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", "false",
                            "message", e.getMessage()
                    ));
        }
    }

    /**
     * Update total paid fees for a student
     */
    @PutMapping(value = "/update-total-paid", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> updateTotalPaid(
            @RequestBody Map<String, Object> request) {

        String regNo = (String) request.get("regNo");
        Double totalPaid = ((Number) request.get("totalPaid")).doubleValue();

        log.info("PUT /api/fees-manager/update-total-paid - regNo: {}, totalPaid: {}", regNo, totalPaid);

        try {
            feesManagerService.updateTotalPaid(regNo, totalPaid);
            return ResponseEntity.ok(Map.of(
                    "success", "true",
                    "message", "Total paid updated successfully"
            ));
        } catch (Exception e) {
            log.error("Error updating total paid", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", "false",
                            "message", e.getMessage()
                    ));
        }
    }

    /**
     *  Get all fees with pagination
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Page<FeesSummaryDTO>> getAllFees(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        log.info("GET /api/fees-manager - page: {}, size: {}", page, size);

        Page<FeesSummaryDTO> fees = feesManagerService.getAllFeesSummary(page, size);
        return ResponseEntity.ok(fees);
    }

    // ==================== FEE RECEIPTS ====================

    /**
     * Create new fee receipt
     */
    @PostMapping(value = "/receipts", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FeeReceiptResponseDTO> createFeeReceipt(
            @Valid @RequestBody FeeReceiptRequestDTO requestDTO) {

        log.info("POST /api/fees-manager/receipts - regNo: {}", requestDTO.getRegNo());

        FeeReceiptResponseDTO receipt = feesManagerService.createFeeReceipt(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(receipt);
    }

    /**
     * Get receipts by registration number
     */
    @GetMapping(value = "/receipts/{regNo}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<FeeReceiptResponseDTO>> getReceiptsByRegNo(
            @PathVariable String regNo) {

        log.info("GET /api/fees-manager/receipts/{}", regNo);

        if (regNo == null || regNo.trim().isEmpty()) {
            log.error(" Registration number is empty");
            return ResponseEntity.badRequest().build();
        }

        try {
            List<FeeReceiptResponseDTO> receipts = feesManagerService.getReceiptsByRegNo(regNo);
            log.info(" Returning {} receipts for regNo: {}", receipts.size(), regNo);
            return ResponseEntity.ok(receipts);
        } catch (Exception e) {
            log.error(" Error fetching receipts for {}: {}", regNo, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Delete fee receipt
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

        log.info("POST /api/fees-manager/refunds - regNo: {}", requestDTO.getRegNo());

        FeeRefundResponseDTO refund = feesManagerService.createFeeRefund(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(refund);
    }

    /**
     * Get refunds by registration number
     */
    @GetMapping(value = "/refunds/{regNo}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<FeeRefundResponseDTO>> getRefundsByRegNo(
            @PathVariable String regNo) {

        log.info("GET /api/fees-manager/refunds/{}", regNo);

        List<FeeRefundResponseDTO> refunds = feesManagerService.getRefundsByRegNo(regNo);
        return ResponseEntity.ok(refunds);
    }

    // ==================== INSTALLMENTS ====================

    /**
     * Get installments by registration number
     */
    @GetMapping(value = "/installments/{regNo}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<FeeInstallmentDTO>> getInstallmentsByRegNo(
            @PathVariable String regNo) {

        log.info("GET /api/fees-manager/installments/{}", regNo);

        List<FeeInstallmentDTO> installments = feesManagerService.getInstallmentsByRegNo(regNo);
        return ResponseEntity.ok(installments);
    }

    // ==================== CSV IMPORT ====================

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

    /**
     * Bulk import fees from CSV
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

    // ==================== EXCEPTION HANDLING ====================

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

    /**
     *  Save fee installments (POST)
     */
    @PostMapping(value = "/installments/{regNo}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> saveFeeInstallments(
            @PathVariable String regNo,
            @Valid @RequestBody FeeInstallmentBatchDTO installmentsDTO) {

        log.info("POST /api/fees-manager/installments/{} - Saving {} installments",
                regNo, installmentsDTO.getInstallments().size());

        try {
            List<FeeInstallmentDTO> saved = feesManagerService.saveFeeInstallments(regNo, installmentsDTO);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Installments saved successfully",
                    "installments", saved
            ));
        } catch (Exception e) {
            log.error("Error saving installments", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "message", e.getMessage()
                    ));
        }
    }

    /**
     *  Send receipt via email with PDF attachment generated from frontend
     */
    @PostMapping(value = "/receipts/{receiptNo}/send-email", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> sendReceiptEmail(
            @PathVariable String receiptNo,
            @RequestBody(required = false) Map<String, Object> emailData) {  // CHANGED: required = false

        log.info("📧 POST /api/fees-manager/receipts/{}/send-email", receiptNo);

        try {
            // ADDED: Check if body is null
            if (emailData == null || emailData.isEmpty()) {
                log.error(" Email data is null or empty");
                return ResponseEntity.badRequest()
                        .body(Map.of("success", "false", "message", "Email data is required"));
            }

            // Validate inputs
            String email = (String) emailData.get("email");
            String message = (String) emailData.get("message");
            String studentName = (String) emailData.get("studentName");
            String pdfData = (String) emailData.get("pdfData");

            log.info("📧 Received email request - Email: {}, StudentName: {}, PDFDataLength: {}",
                    email, studentName, (pdfData != null ? pdfData.length() : 0));

            if (email == null || email.trim().isEmpty()) {
                log.warn(" Email address is missing");
                return ResponseEntity.badRequest()
                        .body(Map.of("success", "false", "message", "Email address is required"));
            }

            if (pdfData == null || pdfData.trim().isEmpty()) {
                log.warn(" PDF data is missing");
                return ResponseEntity.badRequest()
                        .body(Map.of("success", "false", "message", "PDF data is missing"));
            }

            // CHANGED: Better base64 validation with size check
            if (!isValidBase64(pdfData)) {
                log.error(" Invalid base64 PDF data (size: {})", pdfData.length());
                return ResponseEntity.badRequest()
                        .body(Map.of("success", "false", "message", "Invalid PDF data format"));
            }

            // ADDED: Check PDF size (warn if > 5MB)
            int pdfSizeKB = (pdfData.length() * 3) / 4 / 1024;
            log.info("📊 PDF size: {} KB", pdfSizeKB);

            if (pdfSizeKB > 5120) { // 5MB
                log.warn(" PDF size is large: {} KB - may cause issues", pdfSizeKB);
            }

            log.info(" Sending email to {} for receipt {}", email, receiptNo);

            // Call service (async processing)
            feesManagerService.sendReceiptEmail(receiptNo, email, studentName, message, pdfData);

            return ResponseEntity.ok(Map.of(
                    "success", "true",
                    "message", "Email is being sent to " + email
            ));

        } catch (ResourceNotFoundException e) {
            log.error(" Resource not found: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", "false", "message", e.getMessage()));
        } catch (Exception e) {
            log.error(" Error sending receipt email", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", "false", "message", "Failed to send email: " + e.getMessage()));
        }
    }

    /**
     * Validate base64 string
     */
    private boolean isValidBase64(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }

        // CHANGED: Better validation with try-catch
        try {
            // Check if string contains only valid base64 characters
            if (!str.matches("^[A-Za-z0-9+/]*={0,2}$")) {
                log.error(" Invalid base64 characters found");
                return false;
            }

            // Try to decode
            byte[] decoded = Base64.getDecoder().decode(str);

            // Check if decoded data is valid (should be PDF magic bytes)
            if (decoded.length < 4) {
                log.error(" Decoded data too small");
                return false;
            }

            // OPTIONAL: Check PDF magic bytes (25 50 44 46 = %PDF)
            if (decoded[0] == 0x25 && decoded[1] == 0x50 &&
                    decoded[2] == 0x44 && decoded[3] == 0x46) {
                log.info(" Valid PDF signature found");
            }

            return true;
        } catch (IllegalArgumentException e) {
            log.error(" Base64 decode failed: {}", e.getMessage());
            return false;
        }
    }
}