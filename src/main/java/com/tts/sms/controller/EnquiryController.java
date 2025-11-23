package com.tts.sms.controller;

import com.tts.sms.dto.*;
import com.tts.sms.service.EnquiryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * REST Controller for Enquiry management
 * Handles CRUD operations, bulk import, export, and search functionality
 */
@Slf4j
@RestController
@RequestMapping("/api/enquiries")
@RequiredArgsConstructor
public class EnquiryController {

    private final EnquiryService enquiryService;

    /**
     * Get all enquiries with pagination
     * GET /api/enquiries?page=0&size=25
     */
    @GetMapping
    public ResponseEntity<Page<EnquiryResponseDTO>> getAllEnquiries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        log.info("GET /api/enquiries - page: {}, size: {}", page, size);
        Page<EnquiryResponseDTO> enquiries = enquiryService.getAllEnquiries(page, size);
        return ResponseEntity.ok(enquiries);
    }

    /**
     * Search enquiries with filters
     * POST /api/enquiries/search
     */
    @PostMapping("/search")
    public ResponseEntity<Page<EnquiryResponseDTO>> searchEnquiries(
            @Valid @RequestBody EnquirySearchDTO searchDTO) {

        log.info("POST /api/enquiries/search - criteria: {}", searchDTO);
        Page<EnquiryResponseDTO> results = enquiryService.searchEnquiries(searchDTO);
        return ResponseEntity.ok(results);
    }

    /**
     * Get enquiry by ID
     * GET /api/enquiries/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EnquiryResponseDTO> getEnquiryById(@PathVariable Long id) {
        log.info("GET /api/enquiries/{}", id);
        EnquiryResponseDTO enquiry = enquiryService.getEnquiryById(id);
        return ResponseEntity.ok(enquiry);
    }

    /**
     * Create new enquiry
     * POST /api/enquiries
     */
    @PostMapping
    public ResponseEntity<EnquiryResponseDTO> createEnquiry(
            @Valid @RequestBody EnquiryRequestDTO requestDTO) {

        log.info("POST /api/enquiries - mobile: {}", requestDTO.getMobile());
        EnquiryResponseDTO created = enquiryService.createEnquiry(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Update existing enquiry
     * PUT /api/enquiries/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<EnquiryResponseDTO> updateEnquiry(
            @PathVariable Long id,
            @Valid @RequestBody EnquiryRequestDTO requestDTO) {

        log.info("PUT /api/enquiries/{} - mobile: {}", id, requestDTO.getMobile());
        EnquiryResponseDTO updated = enquiryService.updateEnquiry(id, requestDTO);
        return ResponseEntity.ok(updated);
    }

    /**
     * Delete enquiry (soft delete)
     * DELETE /api/enquiries/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteEnquiry(@PathVariable Long id) {
        log.info("DELETE /api/enquiries/{}", id);
        enquiryService.deleteEnquiry(id);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Enquiry deleted successfully"
        ));
    }

    /**
     * Bulk import enquiries from CSV
     * POST /api/enquiries/bulk-import?importType=OLD_FORMAT|NEW_FORMAT
     */
    @PostMapping("/bulk-import")
    public ResponseEntity<BulkImportResponseDTO> bulkImportFromCSV(
            @RequestParam("file") MultipartFile file,
            @RequestParam("importType") BulkImportRequestDTO.ImportType importType) {

        log.info("POST /api/enquiries/bulk-import - file: {}, type: {}",
                file.getOriginalFilename(), importType);

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(BulkImportResponseDTO.builder()
                            .success(false)
                            .message("File is empty")
                            .build());
        }

        if (!file.getOriginalFilename().toLowerCase().endsWith(".csv")) {
            return ResponseEntity.badRequest()
                    .body(BulkImportResponseDTO.builder()
                            .success(false)
                            .message("Only CSV files are supported")
                            .build());
        }

        BulkImportResponseDTO result = enquiryService.bulkImportEnquiries(file, importType);

        HttpStatus status = result.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(result);
    }

    /**
     * Bulk import from JSON body
     * POST /api/enquiries/bulk-import-json
     */
    @PostMapping("/bulk-import-json")
    public ResponseEntity<BulkImportResponseDTO> bulkImportFromJSON(
            @RequestBody List<EnquiryRequestDTO> enquiries,
            @RequestParam(defaultValue = "MANUAL") String importSource) {

        log.info("POST /api/enquiries/bulk-import-json - {} records", enquiries.size());

        // Manual validation for better error handling
        if (enquiries == null || enquiries.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(BulkImportResponseDTO.builder()
                            .success(false)
                            .totalRecords(0)
                            .successfulImports(0)
                            .failedImports(0)
                            .message("Enquiries list cannot be empty")
                            .build());
        }

        BulkImportResponseDTO result = enquiryService.processBulkImport(enquiries, importSource);

        HttpStatus status = result.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(result);
    }

    /**
     * Export enquiries to CSV
     * GET /api/enquiries/export/csv
     */
    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportToCSV() {
        log.info("GET /api/enquiries/export/csv");

        byte[] csvData = enquiryService.exportEnquiriesToCSV();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDisposition(
                ContentDisposition.attachment()
                        .filename("enquiries_" + System.currentTimeMillis() + ".csv")
                        .build()
        );

        return ResponseEntity.ok()
                .headers(headers)
                .body(csvData);
    }

    /**
     * Get enquiry statistics
     * GET /api/enquiries/statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        log.info("GET /api/enquiries/statistics");
        Map<String, Object> stats = enquiryService.getEnquiryStatistics();
        return ResponseEntity.ok(stats);
    }

    /**
     * Health check endpoint
     * GET /api/enquiries/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "Enquiry Service",
                "timestamp", java.time.LocalDateTime.now().toString()
        ));
    }
}