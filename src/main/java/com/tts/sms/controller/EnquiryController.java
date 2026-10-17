package com.tts.sms.controller;

import com.tts.sms.dto.*;
import com.tts.sms.service.CourseService;
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

        log.info("POST /api/enquiries/search - searchTerm: {}, page: {}, size: {}",
                searchDTO.getSearchTerm(), searchDTO.getPage(), searchDTO.getSize());

        try {
            Page<EnquiryResponseDTO> results = enquiryService.searchEnquiries(searchDTO);
            log.info("Search returned {} results", results.getTotalElements());
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Search failed", e);
            throw new RuntimeException("Search operation failed: " + e.getMessage());
        }
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

    // Generic routes come AFTER specific routes

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

    @PostMapping("/{id}/send-whatsapp")
    public ResponseEntity<WhatsAppSendResult> sendWhatsAppCourseInformation(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> request) {
        log.info("POST /api/enquiries/{}/send-whatsapp", id);
        String documentUrl = request != null ? request.get("documentUrl") : null;
        String courseName = request != null ? request.get("courseName") : null;
        String documentFileName = request != null ? request.get("documentFileName") : null;
        WhatsAppSendResult result = enquiryService.sendWhatsAppCourseInformation(id, documentUrl, courseName, documentFileName);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/upload-brochure")
    public ResponseEntity<Map<String, Object>> uploadBrochure(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "courseName", required = false) String courseName) {
        log.info("POST /api/enquiries/upload-brochure - file: {}, courseName: {}", 
                file != null ? file.getOriginalFilename() : "null", courseName);

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "No document file was selected."
            ));
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Invalid file name."
            ));
        }

        String lowerName = originalName.toLowerCase();
        if (!lowerName.endsWith(".pdf") && !lowerName.endsWith(".zip")) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Only PDF documents (.pdf) or ZIP archives (.zip) are supported."
            ));
        }

        if (file.getSize() > 25 * 1024 * 1024) { // 25 MB limit
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "File size exceeds 25MB limit."
            ));
        }

        try {
            String customBrochuresDir = "./uploads/courses/brochures/custom";
            java.nio.file.Path dirPath = java.nio.file.Paths.get(customBrochuresDir).toAbsolutePath().normalize();
            if (!java.nio.file.Files.exists(dirPath)) {
                java.nio.file.Files.createDirectories(dirPath);
            }

            // For custom uploaded files, preserve the original filename cleaned of unsafe characters
            String cleanName = originalName.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
            String uniqueName = System.currentTimeMillis() + "_" + cleanName;
            java.nio.file.Path targetFilePath = dirPath.resolve(uniqueName);
            java.nio.file.Files.copy(file.getInputStream(), targetFilePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            String publicUrl = enquiryService.buildBrochurePublicUrl("brochures/custom/" + uniqueName);

            log.info("Uploaded brochure saved in custom folder: {} -> URL: {}", targetFilePath.toAbsolutePath(), publicUrl);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "fileName", cleanName,
                    "fileType", lowerName.endsWith(".zip") ? "ZIP" : "PDF",
                    "documentUrl", publicUrl,
                    "message", "Brochure uploaded successfully to custom folder"
            ));

        } catch (Exception e) {
            log.error("Failed to save uploaded brochure", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Failed to upload document: " + e.getMessage()
            ));
        }
    }

    /**
     * Get list of custom uploaded brochures and packages from ./uploads/courses/brochures/custom/
     */
    @GetMapping("/custom-brochures")
    public ResponseEntity<?> getCustomBrochures() {
        try {
            java.io.File dir = new java.io.File("./uploads/courses/brochures/custom");
            if (!dir.exists() || !dir.isDirectory()) {
                return ResponseEntity.ok(java.util.Collections.emptyList());
            }
            java.io.File[] files = dir.listFiles((d, name) -> {
                String l = name.toLowerCase();
                return l.endsWith(".pdf") || l.endsWith(".zip");
            });
            if (files == null || files.length == 0) {
                return ResponseEntity.ok(java.util.Collections.emptyList());
            }
            java.util.List<Map<String, Object>> result = new java.util.ArrayList<>();
            for (java.io.File f : files) {
                String cleanName = f.getName().replaceFirst("^\\d+_", "");
                String url = enquiryService.buildBrochurePublicUrl("brochures/custom/" + f.getName());
                result.add(Map.of(
                        "fileName", cleanName,
                        "storedName", f.getName(),
                        "documentUrl", url,
                        "fileType", f.getName().toLowerCase().endsWith(".zip") ? "ZIP" : "PDF",
                        "sizeBytes", f.length()
                ));
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to list custom brochures", e);
            return ResponseEntity.ok(java.util.Collections.emptyList());
        }
    }

    @GetMapping("/preview-brochure")
    public ResponseEntity<?> previewBrochure(@RequestParam(value = "courses", required = false) String courses) {
        if (courses == null || courses.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Course parameter is required"));
        }
        java.util.List<String> courseList = new java.util.ArrayList<>();
        if (courses.contains(",")) {
            for (String c : courses.split(",")) {
                if (!c.trim().isEmpty()) {
                    courseList.add(c.trim());
                }
            }
        } else {
            courseList.add(courses.trim());
        }
        if (courseList.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No valid course found"));
        }
        String docUrl = enquiryService.getConsolidatedBrochureUrl(courseList);
        String firstCourse = courseList.get(0);
        String fileName = CourseService.formatBrochureFileName(firstCourse, ".pdf");
        boolean hasBrochure = (docUrl != null && !docUrl.isBlank());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "hasBrochure", hasBrochure,
                "documentUrl", hasBrochure ? docUrl : "",
                "fileName", fileName,
                "firstCourse", firstCourse
        ));
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