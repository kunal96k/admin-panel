package com.tts.sms.controller;

import com.tts.sms.dto.ManualCertificateLogDTO;
import com.tts.sms.dto.ManualCertificateRequestDTO;
import com.tts.sms.model.Certificate;
import com.tts.sms.service.AutoCertificateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/certificates")
@RequiredArgsConstructor
@Slf4j
public class AutoCertificateController {

    private final AutoCertificateService autoCertificateService;

    /**
     * AUTO-GENERATE CERTIFICATES FOR NEW ADMISSIONS
     */
    @PostMapping(value = "/auto-generate", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> autoGenerateCertificates() {
        Map<String, Object> response = new HashMap<>();

        try {
            log.info("🚀 Manual trigger: Auto generate certificates");

            int created = autoCertificateService.processFeesAndGenerateCertificates();

            response.put("success", true);
            response.put("message", "Auto certificate generation completed");
            response.put("certificatesCreated", created);

            log.info("✅ Successfully created {} certificates", created);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Error in auto certificate generation", e);

            response.put("success", false);
            response.put("message", "Failed to generate certificates: " + e.getMessage());
            response.put("certificatesCreated", 0);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * MANUAL CERTIFICATE GENERATION (BACKUP)
     */
    @PostMapping(value = "/manual-generate", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> manualGenerateCertificate(
            @RequestBody ManualCertificateRequestDTO request) {

        Map<String, Object> response = new HashMap<>();

        try {
            log.info("🔧 Manual certificate generation: {} - {}",
                    request.getRegistrationNo(), request.getCourseName());

            Certificate certificate = autoCertificateService.createManualCertificate(request);

            response.put("success", true);
            response.put("message", "Certificate created manually");
            response.put("certificateId", certificate.getId());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Error in manual certificate generation", e);

            response.put("success", false);
            response.put("message", e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * GET MANUAL CERTIFICATE LOGS
     */
    @GetMapping(value = "/manual-logs", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<ManualCertificateLogDTO>> getManualLogs() {
        try {
            List<ManualCertificateLogDTO> logs = autoCertificateService.getManualCertificateLogs();
            return ResponseEntity.ok(logs);
        } catch (Exception e) {
            log.error("❌ Error fetching manual logs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping(value = "/manual-logs/paged", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Page<ManualCertificateLogDTO>> getManualLogsPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        try {
            Page<ManualCertificateLogDTO> logs = autoCertificateService.getManualCertificateLogsPaginated(page, size);
            return ResponseEntity.ok(logs);
        } catch (Exception e) {
            log.error("❌ Error fetching manual logs paged", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * BULK GENERATE CERTIFICATES FOR SELECTED STUDENTS
     * Accepts a list of registration numbers and creates certificates for each student's courses.
     */
    @PostMapping(value = "/bulk-generate", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> bulkGenerateCertificates(
            @RequestBody Map<String, List<String>> request) {

        Map<String, Object> response = new HashMap<>();

        try {
            List<String> registrationNumbers = request.get("registrationNumbers");

            if (registrationNumbers == null || registrationNumbers.isEmpty()) {
                response.put("success", false);
                response.put("message", "No registration numbers provided");
                response.put("certificatesCreated", 0);
                return ResponseEntity.badRequest().body(response);
            }

            log.info("🚀 Bulk generate certificates for {} students", registrationNumbers.size());

            int created = autoCertificateService.bulkGenerateCertificates(registrationNumbers);

            response.put("success", created > 0);
            response.put("certificatesCreated", created);
            response.put("message", created > 0
                    ? created + " certificate(s) created successfully"
                    : "No new certificates were created. Students may already have certificates.");

            log.info("✅ Bulk generation complete: {} certificates created", created);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Error in bulk certificate generation", e);

            response.put("success", false);
            response.put("message", "Failed to generate certificates: " + e.getMessage());
            response.put("certificatesCreated", 0);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}