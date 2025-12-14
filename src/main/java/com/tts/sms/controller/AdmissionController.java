package com.tts.sms.controller;

import com.tts.sms.dto.*;
import com.tts.sms.service.AdmissionService;
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
@RequestMapping("/api/admissions")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AdmissionController {

    private final AdmissionService admissionService;

    /**
     * Transfer admission to new academic year/batch
     */
    @PostMapping("/transfer")
    public ResponseEntity<AdmissionResponseDTO> transferAdmission(
            @Valid @RequestBody TransferAdmissionDTO transferDTO) {

        log.info("POST /api/admissions/transfer - id: {}", transferDTO.getAdmissionId());

        AdmissionResponseDTO transferred = admissionService.transferAdmission(transferDTO);
        return ResponseEntity.ok(transferred);
    }

    /**
     *  Manually change student category (Admin override)
     */
    @PutMapping(value = "/{id}/category", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AdmissionResponseDTO> updateStudentCategory(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {

        String newCategory = request.get("category");

        log.info("PUT /api/admissions/{}/category - {}", id, newCategory);

        if (newCategory == null || newCategory.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            AdmissionResponseDTO updated = admissionService.updateStudentCategory(id, newCategory);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            log.error("Invalid category: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all admissions for export (with fees data)
     */
    @GetMapping(value = "/export", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<AdmissionExportDTO>> getAllAdmissionsForExport() {
        log.info("GET /api/admissions/export");
        List<AdmissionExportDTO> admissions = admissionService.getAllAdmissionsForExport();
        return ResponseEntity.ok(admissions);
    }

    /**
     * Check if admission can be created for mobile number
     */
    @GetMapping("/can-create/{mobileNumber}")
    public ResponseEntity<Map<String, Object>> canCreateAdmission(
            @PathVariable String mobileNumber) {

        log.info("GET /api/admissions/can-create/{}", mobileNumber);

        boolean canCreate = admissionService.canCreateAdmission(mobileNumber);

        return ResponseEntity.ok(Map.of(
                "canCreate", canCreate,
                "message", canCreate
                        ? "Admission can be created"
                        : "Cannot create admission. Either enquiry not found or admission already exists"
        ));
    }

    /**
     * Bulk import admissions from CSV
     */
    @PostMapping("/bulk-import")
    public ResponseEntity<BulkImportResponseDTO> bulkImportFromCSV(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "OLD_FORMAT") String importType) {

        log.info("POST /api/admissions/bulk-import - file: {}, type: {}",
                file.getOriginalFilename(), importType);

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(BulkImportResponseDTO.builder()
                            .success(false)
                            .message("File is empty")
                            .build());
        }

        BulkImportResponseDTO result = admissionService.bulkImportAdmissions(file, importType);

        HttpStatus status = result.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(result);
    }

    /**
     * Bulk import admissions from JSON (for client-side parsing)
     */
    @PostMapping(value = "/bulk-import-json",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BulkImportResponseDTO> bulkImportFromJSON(
            @RequestBody List<AdmissionRequestDTO> admissions,
            @RequestParam(defaultValue = "OLD_FORMAT") String importSource) {

        log.info("POST /api/admissions/bulk-import-json - {} records", admissions.size());

        if (admissions == null || admissions.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(BulkImportResponseDTO.builder()
                            .success(false)
                            .totalRecords(0)
                            .successfulImports(0)
                            .failedImports(0)
                            .message("Admissions list cannot be empty")
                            .build());
        }

        BulkImportResponseDTO result = admissionService.processBulkAdmissionImport(admissions, importSource);

        HttpStatus status = result.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(result);
    }

    /**
     * Get all admissions with pagination
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Page<AdmissionResponseDTO>> getAllAdmissions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        log.debug("GET /api/admissions - page: {}, size: {}", page, size);
        Page<AdmissionResponseDTO> admissions = admissionService.getAllAdmissions(page, size);
        return ResponseEntity.ok(admissions);
    }

    /**
     * Search admissions
     */
    @PostMapping(value = "/search",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Page<AdmissionResponseDTO>> searchAdmissions(
            @RequestBody AdmissionSearchDTO searchDTO) {

        log.debug("POST /api/admissions/search - {}", searchDTO);

        try {
            Page<AdmissionResponseDTO> results = admissionService.searchAdmissions(searchDTO);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Search error: ", e);
            // Return empty page instead of error
            return ResponseEntity.ok(Page.empty());
        }
    }

    /**
     * Get admission by ID
     */
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AdmissionResponseDTO> getAdmissionById(@PathVariable Long id) {
        log.debug("GET /api/admissions/{}", id);
        AdmissionResponseDTO admission = admissionService.getAdmissionById(id);
        return ResponseEntity.ok(admission);
    }

    /**
     * Create new admission
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AdmissionResponseDTO> createAdmission(
            @Valid @RequestBody AdmissionRequestDTO requestDTO) {

        log.debug("POST /api/admissions - Creating admission");
        AdmissionResponseDTO created = admissionService.createAdmission(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Update admission
     */
    @PutMapping(value = "/{id}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AdmissionResponseDTO> updateAdmission(
            @PathVariable Long id,
            @Valid @RequestBody AdmissionRequestDTO requestDTO) {

        log.debug("PUT /api/admissions/{}", id);
        AdmissionResponseDTO updated = admissionService.updateAdmission(id, requestDTO);
        return ResponseEntity.ok(updated);
    }

    /**
     * Delete admission
     */
    @DeleteMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> deleteAdmission(@PathVariable Long id) {
        log.debug("DELETE /api/admissions/{}", id);
        admissionService.deleteAdmission(id);
        return ResponseEntity.ok(Map.of("message", "Admission deleted successfully"));
    }

    /**
     * Get enquiry data for pre-fill
     */
    @GetMapping(value = "/enquiry-data/{mobile}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EnquiryResponseDTO> getEnquiryForAdmission(@PathVariable String mobile) {
        log.debug("GET /api/admissions/enquiry-data/{}", mobile);
        EnquiryResponseDTO enquiry = admissionService.getEnquiryForAdmission(mobile);
        return ResponseEntity.ok(enquiry);
    }

    /**
     *  : Get installments by admission ID
     * - First fetch admission to get registrationNumber
     * - Then fetch installments using registrationNumber
     */
    @GetMapping(value = "/{id}/installments", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<FeeInstallmentDTO>> getInstallments(@PathVariable Long id) {
        log.debug("GET /api/admissions/{}/installments", id);
        List<FeeInstallmentDTO> installments = admissionService.getInstallments(id);
        return ResponseEntity.ok(installments);
    }

    /**
     * Get admission by registration number
     */
    @GetMapping(value = "/by-regno/{regNo}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AdmissionResponseDTO> getByRegNo(@PathVariable String regNo) {
        log.debug("GET /api/admissions/by-regno/{}", regNo);

        AdmissionResponseDTO admission = admissionService.getByRegistrationNumber(regNo);
        return ResponseEntity.ok(admission);
    }

    /**
     *  : Generate installments for admission
     * - Uses registrationNumber instead of admissionId
     */
    @PostMapping(value = "/{id}/installments",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<FeeInstallmentDTO>> generateInstallments(
            @PathVariable Long id,
            @Valid @RequestBody InstallmentConfigDTO config) {

        log.debug("POST /api/admissions/{}/installments", id);

        //  Get admission first to retrieve registrationNumber
        AdmissionResponseDTO admission = admissionService.getAdmissionById(id);

        //  Use registrationNumber instead of id
        List<FeeInstallmentDTO> installments = admissionService.generateInstallments(
                admission.getRegistrationNumber(), //  CHANGED FROM id
                config,
                admission.getTotalReceivableFees()
        );

        return ResponseEntity.ok(installments);
    }

    /**
     * Get statistics
     */
    @GetMapping(value = "/statistics", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getStatistics() {
        log.debug("GET /api/admissions/statistics");
        Map<String, Object> stats = admissionService.getAdmissionStatistics();
        return ResponseEntity.ok(stats);
    }

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