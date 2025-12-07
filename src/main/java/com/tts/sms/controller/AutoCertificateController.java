package com.tts.sms.controller;

import com.tts.sms.dto.ManualCertificateLogDTO;
import com.tts.sms.dto.ManualCertificateRequestDTO;
import com.tts.sms.model.Certificate;
import com.tts.sms.service.AutoCertificateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
}