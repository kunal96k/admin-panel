package com.tts.sms.controller;

import com.tts.sms.dto.CertificateDTO;
import com.tts.sms.service.CertificateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * REST API Controller for Certificate Management
 */
@RestController
@RequestMapping("/api/certificates")
@RequiredArgsConstructor
@Slf4j
public class CertificateRestController {

    private final CertificateService certificateService;

    /**
     * Receive canvas image data from frontend and send email
     */
    @PostMapping("/{id}/send-email-with-image")
    public ResponseEntity<?> sendCertificateEmailWithImage(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            String imageData = request.get("imageData"); // base64 image from canvas

            if (email == null || email.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Email address is required"));
            }

            if (imageData == null || imageData.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Certificate image data is required"));
            }

            // Remove data URL prefix if present
            if (imageData.startsWith("data:image")) {
                imageData = imageData.substring(imageData.indexOf(",") + 1);
            }

            byte[] imageBytes = Base64.getDecoder().decode(imageData);

            certificateService.sendCertificateEmailWithImage(id, email, imageBytes);

            return ResponseEntity.ok(Map.of("message", "Certificate sent successfully"));

        } catch (RuntimeException e) {
            log.error("Error sending certificate email", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error sending certificate email", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to send email: " + e.getMessage()));
        }
    }

    /**
     * Get all certificates with pagination and filters
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllCertificates(
            @RequestParam(required = false) String course,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        try {
            Page<CertificateDTO> certificatePage = certificateService.getCertificates(
                    course, status, search, page, size);

            Map<String, Object> response = new HashMap<>();
            response.put("certificates", certificatePage.getContent());
            response.put("currentPage", certificatePage.getNumber());
            response.put("totalItems", certificatePage.getTotalElements());
            response.put("totalPages", certificatePage.getTotalPages());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching certificates", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch certificates: " + e.getMessage()));
        }
    }

    /**
     * Send certificate via email
     */
    @PostMapping("/{id}/send-email")
    public ResponseEntity<?> sendCertificateEmail(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            if (email == null || email.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Email address is required"));
            }

            certificateService.sendCertificateEmail(id, email);
            return ResponseEntity.ok(Map.of("message", "Certificate sent successfully"));

        } catch (RuntimeException e) {
            log.error("Error sending certificate email", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error sending certificate email", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to send email: " + e.getMessage()));
        }
    }

    /**
     * Get certificate statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getStatistics() {
        try {
            Map<String, Long> stats = certificateService.getStatistics();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error fetching statistics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch statistics: " + e.getMessage()));
        }
    }

    /**
     * Get certificate by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getCertificateById(@PathVariable Long id) {
        try {
            CertificateDTO certificate = certificateService.getCertificateById(id);
            return ResponseEntity.ok(certificate);
        } catch (RuntimeException e) {
            log.error("Certificate not found: {}", id, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error fetching certificate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch certificate: " + e.getMessage()));
        }
    }

    /**
     * Issue certificate
     */
    @PutMapping("/{id}/issue")
    public ResponseEntity<?> issueCertificate(
            @PathVariable Long id,
            @RequestBody CertificateDTO dto) {
        try {
            CertificateDTO certificate = certificateService.issueCertificate(id, dto);
            return ResponseEntity.ok(certificate);
        } catch (RuntimeException e) {
            log.error("Error issuing certificate", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error issuing certificate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to issue certificate: " + e.getMessage()));
        }
    }

    /**
     * Delete certificate
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCertificate(@PathVariable Long id) {
        try {
            certificateService.deleteCertificate(id);
            return ResponseEntity.ok(Map.of("message", "Certificate deleted successfully"));
        } catch (RuntimeException e) {
            log.error("Error deleting certificate", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error deleting certificate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete certificate: " + e.getMessage()));
        }
    }

    /**
     * Import certificates from CSV
     */
    @PostMapping("/import")
    public ResponseEntity<?> importCertificates(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Please select a CSV file to upload"));
            }

            String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
            if (!filename.endsWith(".csv")) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Only CSV files are allowed"));
            }

            Map<String, Object> result = certificateService.importCertificatesFromCSV(file);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Error importing certificates", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to import certificates: " + e.getMessage()));
        }
    }

    /**
     * Get filtered statistics based on current view
     */
    @GetMapping("/stats-filtered")
    public ResponseEntity<Map<String, Long>> getFilteredStatistics(
            @RequestParam(required = false) String course,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {
        try {
            Map<String, Long> stats = certificateService.getFilteredStatistics(course, status, search);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error fetching filtered statistics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("total", 0L, "issued", 0L, "pending", 0L));
        }
    }

    /**
     * Export certificates to CSV
     */
    @GetMapping("/export/csv")
    public ResponseEntity<?> exportCertificates() {
        try {
            byte[] csvData = certificateService.exportCertificatesToCSV();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("text/csv"));
            headers.setContentDispositionFormData("attachment",
                    "certificates_" + System.currentTimeMillis() + ".csv");

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(csvData);

        } catch (RuntimeException e) {
            log.error("Error exporting certificates", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error exporting certificates", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to export certificates: " + e.getMessage()));
        }
    }
}
