package com.tts.sms.controller;

import com.tts.sms.dto.*;
import com.tts.sms.service.EnquiryService;
import com.tts.sms.service.FollowUpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/enquiries")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EnquiryController {

    private final EnquiryService enquiryService;
    private final FollowUpService followUpService;

    // ✅ CRITICAL: Specific routes MUST come BEFORE /{id} route

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "Enquiry Service",
                "timestamp", java.time.LocalDateTime.now().toString()
        ));
    }

    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        log.info("GET /api/enquiries/statistics");
        Map<String, Object> stats = enquiryService.getEnquiryStatistics();
        return ResponseEntity.ok(stats);
    }

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
        return ResponseEntity.ok().headers(headers).body(csvData);
    }

    @PostMapping("/search")
    public ResponseEntity<Page<EnquiryResponseDTO>> searchEnquiries(
            @Valid @RequestBody EnquirySearchDTO searchDTO) {
        log.info("POST /api/enquiries/search - criteria: {}", searchDTO);
        Page<EnquiryResponseDTO> results = enquiryService.searchEnquiries(searchDTO);
        return ResponseEntity.ok(results);
    }

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

    @PostMapping("/bulk-import-json")
    public ResponseEntity<BulkImportResponseDTO> bulkImportFromJSON(
            @RequestBody List<EnquiryRequestDTO> enquiries,
            @RequestParam(defaultValue = "MANUAL") String importSource) {

        log.info("POST /api/enquiries/bulk-import-json - {} records, source: {}",
                enquiries.size(), importSource);

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

        try {
            BulkImportResponseDTO result = enquiryService.processBulkImport(enquiries, importSource);
            HttpStatus status = result.isSuccess() ? HttpStatus.OK : HttpStatus.PARTIAL_CONTENT;
            return ResponseEntity.status(status).body(result);
        } catch (Exception e) {
            log.error("Bulk import failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BulkImportResponseDTO.builder()
                            .success(false)
                            .totalRecords(enquiries.size())
                            .successfulImports(0)
                            .failedImports(enquiries.size())
                            .message("Import failed: " + e.getMessage())
                            .build());
        }
    }

    // ✅ Generic routes come AFTER specific routes

    @GetMapping
    public ResponseEntity<Page<EnquiryResponseDTO>> getAllEnquiries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        log.info("GET /api/enquiries - page: {}, size: {}", page, size);
        Page<EnquiryResponseDTO> enquiries = enquiryService.getAllEnquiries(page, size);
        return ResponseEntity.ok(enquiries);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EnquiryResponseDTO> getEnquiryById(@PathVariable Long id) {
        log.info("GET /api/enquiries/{}", id);
        EnquiryResponseDTO enquiry = enquiryService.getEnquiryById(id);
        return ResponseEntity.ok(enquiry);
    }

    @PostMapping
    public ResponseEntity<EnquiryResponseDTO> createEnquiry(
            @Valid @RequestBody EnquiryRequestDTO requestDTO) {
        log.info("POST /api/enquiries - mobile: {}", requestDTO.getMobile());
        EnquiryResponseDTO created = enquiryService.createEnquiry(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<EnquiryResponseDTO> updateEnquiry(
            @PathVariable Long id,
            @Valid @RequestBody EnquiryRequestDTO requestDTO) {
        log.info("PUT /api/enquiries/{} - mobile: {}", id, requestDTO.getMobile());
        EnquiryResponseDTO updated = enquiryService.updateEnquiry(id, requestDTO);
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> updateEnquiryStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        log.info("PATCH /api/enquiries/{}/status - status: {}", id, request.get("status"));
        try {
            String status = request.get("status");
            if (status == null || status.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Status is required"));
            }
            EnquiryResponseDTO updated = enquiryService.updateEnquiryStatus(id, status);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Status updated successfully",
                    "data", updated
            ));
        } catch (Exception e) {
            log.error("Error updating status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteEnquiry(@PathVariable Long id) {
        log.info("DELETE /api/enquiries/{}", id);
        enquiryService.deleteEnquiry(id);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Enquiry deleted successfully"
        ));
    }

    // ================= FOLLOW-UP APIs =================

    @GetMapping("/{enquiryId}/followups")
    public ResponseEntity<?> getFollowUpHistory(@PathVariable Long enquiryId) {
        log.info("GET /api/enquiries/{}/followups", enquiryId);
        try {
            List<FollowUpDTO> history = followUpService.getFollowUpHistory(enquiryId);
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            log.error("Error fetching follow-up history", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/{enquiryId}/followups")
    public ResponseEntity<?> addFollowUp(
            @PathVariable Long enquiryId,
            @RequestBody FollowUpDTO followUp) {
        log.info("POST /api/enquiries/{}/followups - mode: {}", enquiryId, followUp.getMode());
        try {
            followUp.setEnquiryId(enquiryId);
            FollowUpDTO created = followUpService.addFollowUp(enquiryId, followUp);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "success", true,
                    "message", "Follow-up saved successfully",
                    "data", created
            ));
        } catch (Exception e) {
            log.error("Error adding follow-up", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @DeleteMapping("/followups/{followUpId}")
    public ResponseEntity<Map<String, Object>> deleteFollowUp(@PathVariable Long followUpId) {
        log.info("DELETE /api/enquiries/followups/{}", followUpId);
        try {
            followUpService.deleteFollowUp(followUpId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Follow-up deleted successfully"
            ));
        } catch (Exception e) {
            log.error("Error deleting follow-up", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}