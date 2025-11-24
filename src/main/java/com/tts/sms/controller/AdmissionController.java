    package com.tts.sms.controller;

    import com.tts.sms.dto.*;
    import com.tts.sms.service.AdmissionService;
    import jakarta.validation.Valid;
    import lombok.RequiredArgsConstructor;
    import lombok.extern.slf4j.Slf4j;
    import org.springframework.data.domain.Page;
    import org.springframework.http.HttpStatus;
    import org.springframework.http.ResponseEntity;
    import org.springframework.web.bind.annotation.*;

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
         * Get all admissions with pagination
         */
        @GetMapping
        public ResponseEntity<Page<AdmissionResponseDTO>> getAllAdmissions(
                @RequestParam(defaultValue = "0") int page,
                @RequestParam(defaultValue = "25") int size) {

            log.info("GET /api/admissions - page: {}, size: {}", page, size);

            Page<AdmissionResponseDTO> admissions = admissionService.getAllAdmissions(page, size);
            return ResponseEntity.ok(admissions);
        }

        /**
         * Search admissions with filters
         */
        @PostMapping("/search")
        public ResponseEntity<Page<AdmissionResponseDTO>> searchAdmissions(
                @RequestBody AdmissionSearchDTO searchDTO) {

            log.info("POST /api/admissions/search - {}", searchDTO);

            Page<AdmissionResponseDTO> results = admissionService.searchAdmissions(searchDTO);
            return ResponseEntity.ok(results);
        }

        /**
         * Get admission by ID
         */
        @GetMapping("/{id}")
        public ResponseEntity<AdmissionResponseDTO> getAdmissionById(@PathVariable Long id) {
            log.info("GET /api/admissions/{}", id);

            AdmissionResponseDTO admission = admissionService.getAdmissionById(id);
            return ResponseEntity.ok(admission);
        }

        /**
         * Create new admission
         * REQUIRES: Enquiry must exist for the mobile number
         */
        @PostMapping
        public ResponseEntity<?> createAdmission(
                @Valid @RequestBody AdmissionRequestDTO requestDTO) {

            log.info("POST /api/admissions - mobile: {}", requestDTO.getMobilePrimary());

            try {
                AdmissionResponseDTO created = admissionService.createAdmission(requestDTO);
                return ResponseEntity.status(HttpStatus.CREATED).body(created);

            } catch (IllegalArgumentException e) {
                log.error("Validation error: {}", e.getMessage());
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(Map.of(
                                "success", false,
                                "message", e.getMessage(),
                                "errorType", "ENQUIRY_NOT_FOUND"
                        ));
            }
        }

        /**
         * Update existing admission
         */
        @PutMapping("/{id}")
        public ResponseEntity<AdmissionResponseDTO> updateAdmission(
                @PathVariable Long id,
                @Valid @RequestBody AdmissionRequestDTO requestDTO) {

            log.info("PUT /api/admissions/{}", id);

            AdmissionResponseDTO updated = admissionService.updateAdmission(id, requestDTO);
            return ResponseEntity.ok(updated);
        }

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
         * Delete admission (soft delete)
         */
        @DeleteMapping("/{id}")
        public ResponseEntity<Void> deleteAdmission(@PathVariable Long id) {
            log.info("DELETE /api/admissions/{}", id);

            admissionService.deleteAdmission(id);
            return ResponseEntity.noContent().build();
        }

        /**
         * Generate fee installments
         */
        @PostMapping("/{id}/installments")
        public ResponseEntity<List<FeeInstallmentDTO>> generateInstallments(
                @PathVariable Long id,
                @Valid @RequestBody InstallmentConfigDTO config) {

            log.info("POST /api/admissions/{}/installments", id);

            AdmissionResponseDTO admission = admissionService.getAdmissionById(id);
            List<FeeInstallmentDTO> installments = admissionService.generateInstallments(
                    id, config, admission.getTotalReceivableFees());

            return ResponseEntity.ok(installments);
        }

        /**
         * Get installments for admission
         */
        @GetMapping("/{id}/installments")
        public ResponseEntity<List<FeeInstallmentDTO>> getInstallments(@PathVariable Long id) {
            log.info("GET /api/admissions/{}/installments", id);

            List<FeeInstallmentDTO> installments = admissionService.getInstallments(id);
            return ResponseEntity.ok(installments);
        }

        /**
         * Check if admission can be created for mobile number
         * Validates: 1) Enquiry exists, 2) Admission doesn't exist
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
         * Get enquiry data for pre-filling admission form
         */
        @GetMapping("/enquiry-data/{mobileNumber}")
        public ResponseEntity<?> getEnquiryForAdmission(@PathVariable String mobileNumber) {
            log.info("GET /api/admissions/enquiry-data/{}", mobileNumber);

            try {
                EnquiryResponseDTO enquiry = admissionService.getEnquiryForAdmission(mobileNumber);
                return ResponseEntity.ok(enquiry);

            } catch (Exception e) {
                log.error("Error fetching enquiry: {}", e.getMessage());
                return ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body(Map.of(
                                "success", false,
                                "message", "No enquiry found for mobile: " + mobileNumber
                        ));
            }
        }

        /**
         * Get admission statistics
         */
        @GetMapping("/statistics")
        public ResponseEntity<Map<String, Object>> getStatistics() {
            log.info("GET /api/admissions/statistics");

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