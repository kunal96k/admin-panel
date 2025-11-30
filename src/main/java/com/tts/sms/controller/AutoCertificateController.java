package com.tts.sms.controller;

import com.tts.sms.dto.ManualCertificateLogDTO;
import com.tts.sms.dto.ManualCertificateRequestDTO;
import com.tts.sms.model.Certificate;
import com.tts.sms.service.AutoCertificateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/certificates")
@RequiredArgsConstructor
@Slf4j
public class AutoCertificateController {

    private final AutoCertificateService autoCertificateService;

    /**
     * ✅ AUTO-GENERATE CERTIFICATES FOR NEW ADMISSIONS
     */
    @PostMapping("/auto-generate")
    public ResponseEntity<?> autoGenerateCertificates() {
        try {
            log.info("🚀 Manual trigger: Auto generate certificates");

            int created = autoCertificateService.processFeesAndGenerateCertificates();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Auto certificate generation completed",
                    "certificatesCreated", created
            ));

        } catch (Exception e) {
            log.error("❌ Error in auto certificate generation", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                            "success", false,
                            "message", "Failed to generate certificates: " + e.getMessage()
                    ));
        }
    }

    /**
     * ✅ MANUAL CERTIFICATE GENERATION (BACKUP)
     */
    @PostMapping("/manual-generate")
    public ResponseEntity<?> manualGenerateCertificate(
            @RequestBody ManualCertificateRequestDTO request) {
        try {
            log.info("🔧 Manual certificate generation: {} - {}",
                    request.getRegistrationNo(), request.getCourseName());

            Certificate certificate = autoCertificateService.createManualCertificate(request);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Certificate created manually",
                    "certificateId", certificate.getId()
            ));

        } catch (Exception e) {
            log.error("❌ Error in manual certificate generation", e);
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "success", false,
                            "message", e.getMessage()
                    ));
        }
    }

    /**
     * ✅ GET MANUAL CERTIFICATE LOGS
     */
    @GetMapping("/manual-logs")
    public ResponseEntity<List<ManualCertificateLogDTO>> getManualLogs() {
        try {
            List<ManualCertificateLogDTO> logs = autoCertificateService.getManualCertificateLogs();
            return ResponseEntity.ok(logs);
        } catch (Exception e) {
            log.error("❌ Error fetching manual logs", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}