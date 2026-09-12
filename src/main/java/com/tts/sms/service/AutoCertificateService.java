package com.tts.sms.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tts.sms.dto.ManualCertificateLogDTO;
import com.tts.sms.dto.ManualCertificateRequestDTO;
import com.tts.sms.model.Admission;
import com.tts.sms.model.Certificate;
import com.tts.sms.model.Employee;
import com.tts.sms.model.Fees;
import com.tts.sms.model.ManualCertificateLog;
import com.tts.sms.model.User;
import com.tts.sms.repository.AdmissionRepository;
import com.tts.sms.repository.CertificateRepository;
import com.tts.sms.repository.CourseRepository;
import com.tts.sms.repository.EmployeeRepository;
import com.tts.sms.repository.FeesRepository;
import com.tts.sms.repository.ManualCertificateLogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AutoCertificateService {

    private final FeesRepository feesRepository;
    private final CertificateRepository certificateRepository;
    private final AdmissionRepository admissionRepository;
    private final CourseRepository courseRepository;
    private final ManualCertificateLogRepository manualCertificateLogRepository;
    private final EmployeeRepository employeeRepository;
    private final SystemConfigurationService systemConfigurationService;
    private final StudentCategoryService studentCategoryService;

    /**
     * Update admission status to COMPLETED when all certificates issued
     */
    @Transactional
    public void updateAdmissionStatusAfterCertificate(String registrationNumber) {
        log.info("[SEARCH] Checking if {} should be marked COMPLETED", registrationNumber);

        try {
            Admission admission = admissionRepository.findByRegistrationNumberAndIsDeletedFalse(registrationNumber);

            if (admission == null) {
                log.warn(" Admission not found: {}", registrationNumber);
                return;
            }

            // Recalculate category
            LocalDate cutoffDate = systemConfigurationService.getCutoffDate();
            String newCategory = studentCategoryService.determineCategory(admission, cutoffDate);

            if (!newCategory.equals(admission.getStudentCategory())) {
                admission.setStudentCategory(newCategory);
                admission.setCategoryUpdatedAt(LocalDateTime.now());
                admissionRepository.save(admission);

                log.info(" Updated {} category: {} -> {}",
                        registrationNumber, admission.getStudentCategory(), newCategory);
            }

        } catch (Exception e) {
            log.error(" Error updating admission status for {}: {}", registrationNumber, e.getMessage());
        }
    }

    /**
     *  AUTO-GENERATE CERTIFICATES FROM CLEARED FEES
     * - Processes BOTH new and old admissions
     * - Creates separate certificate for EACH course
     * - Skips duplicate certificates (same regNo + course combination)
     */
    @Transactional
    public int processFeesAndGenerateCertificates() {
        log.info("[SYNC] Starting auto certificate generation for admissions with cleared fees...");

        List<Fees> clearedFees = feesRepository.findFeesWithStatusClear();
        log.info("[STATS] Found {} cleared fee records", clearedFees.size());

        int certificatesCreated = 0;

        for (Fees fee : clearedFees) {
            try {
                String regNo = fee.getRegistrationNumber();

                if (regNo == null || regNo.trim().isEmpty()) {
                    continue;
                }

                Admission admission = admissionRepository.findByRegistrationNumberAndIsDeletedFalse(regNo);

                if (admission == null) {
                    log.warn("[WARN] Admission not found for regNo: {}", regNo);
                    continue;
                }

                List<String> courses = normalizeCourseList(admission.getCourses());
                
                if (courses.isEmpty()) {
                    log.warn("[WARN] No courses found for regNo: {}", regNo);
                    continue;
                }

                //  CREATE CERTIFICATE FOR EACH COURSE
                for (String courseName : courses) {
                    boolean exists = certificateRepository.existsByRegistrationNoAndCourseNameAndIsActiveTrue(
                            regNo, courseName
                    );

                    if (exists) {
                        log.debug("[SKIP] Certificate already exists for {} - {}", regNo, courseName);
                        continue;
                    }

                    Certificate certificate = createCertificateFromAdmission(admission, courseName);
                    certificateRepository.save(certificate);

                    certificatesCreated++;
                    log.info(" Created certificate for {} - {} ({})",
                            admission.getFullName(), courseName, regNo);
                }

            } catch (Exception e) {
                log.error(" Error creating certificate for {}: {}",
                        fee.getRegistrationNumber(), e.getMessage());
            }
        }

        log.info(" Auto certificate generation complete: {} certificates created", certificatesCreated);
        return certificatesCreated;
    }

    /**
     *  MANUAL CERTIFICATE GENERATION
     * - For backup cases when auto-generation misses a record
     * - Logs who created it and why
     */
    @Transactional
    public Certificate createManualCertificate(ManualCertificateRequestDTO request) {
        log.info("[CONFIG] Manual certificate generation for: {} - {}",
                request.getRegistrationNo(), request.getCourseName());

        // Get current logged-in user
        Employee currentEmployee = getCurrentEmployee();

        // Check if certificate already exists
        boolean exists = certificateRepository.existsByRegistrationNoAndCourseNameAndIsActiveTrue(
                request.getRegistrationNo(), request.getCourseName()
        );

        if (exists) {
            throw new RuntimeException("Certificate already exists for " +
                    request.getRegistrationNo() + " - " + request.getCourseName());
        }

        // Create certificate
        Certificate certificate = new Certificate();
        certificate.setRegistrationNo(request.getRegistrationNo());
        certificate.setStudentName(request.getStudentName());
        certificate.setCourseName(request.getCourseName());
        certificate.setStatus("Not Issued");
        certificate.setIsActive(true);

        // Try to fetch additional details from admission
        Admission admission = admissionRepository.findByRegistrationNumberAndIsDeletedFalse(
                request.getRegistrationNo()
        );

        if (admission != null) {
            certificate.setStudentEmail(admission.getEmailPrimary());
            certificate.setBatch(generateBatchName(admission.getAdmissionDate()));
        } else {
            certificate.setBatch(generateBatchName(LocalDate.now()));
        }

        // Link to course entity
        courseRepository.findByCourseNameAndIsActiveTrue(request.getCourseName())
                .ifPresent(certificate::setCourse);

        Certificate savedCertificate = certificateRepository.save(certificate);

        //  Create audit log
        ManualCertificateLog log = ManualCertificateLog.builder()
                .registrationNo(request.getRegistrationNo())
                .studentName(request.getStudentName())
                .courseName(request.getCourseName())
                .createdByEmployeeId(currentEmployee.getId())
                .createdByEmployeeName(currentEmployee.getEmployeeName())
                .reason(request.getReason())
                .certificateId(savedCertificate.getId())
                .build();

        manualCertificateLogRepository.save(log);

        return savedCertificate;
    }

    /**
     *  GET MANUAL CERTIFICATE LOGS
     */
    @Transactional(readOnly = true)
    public List<ManualCertificateLogDTO> getManualCertificateLogs() {
        List<ManualCertificateLog> logs = manualCertificateLogRepository
                .findAllByIsActiveTrueOrderByCreatedAtDesc();

        return logs.stream()
                .map(this::convertLogToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<ManualCertificateLogDTO> getManualCertificateLogsPaginated(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        return manualCertificateLogRepository
                .findAllByIsActiveTrueOrderByCreatedAtDesc(pageable)
                .map(this::convertLogToDTO);
    }

    /**
     *  CREATE CERTIFICATE FROM ADMISSION DATA
     */
    private Certificate createCertificateFromAdmission(Admission admission, String courseName) {
        Certificate certificate = new Certificate();
        certificate.setRegistrationNo(admission.getRegistrationNumber());
        certificate.setStudentName(admission.getFullName());
        certificate.setCourseName(courseName);
        certificate.setStudentEmail(admission.getEmailPrimary());
        
        // Use actual batches from admission if available, otherwise generate fallback
        if (admission.getBatches() != null && !admission.getBatches().isEmpty()) {
            certificate.setBatch(String.join(", ", admission.getBatches()));
        } else {
            certificate.setBatch(generateBatchName(admission.getAdmissionDate()));
        }
        
        certificate.setStatus("Not Issued");
        certificate.setIsActive(true);

        courseRepository.findByCourseNameAndIsActiveTrue(courseName)
                .ifPresent(certificate::setCourse);

        return certificate;
    }

    /**
     *  CHECK IF FEES ARE CLEARED
     */
    private boolean isFeesCleared(Fees fee) {
        if ("Clear".equalsIgnoreCase(fee.getStatus())) {
            return true;
        }

        if (fee.getFeesDue() == null || fee.getFeesDue() <= 0.01) {
            return true;
        }

        if (fee.getTotalFees() != null && fee.getTotalPaid() != null) {
            double difference = Math.abs(fee.getTotalFees() - fee.getTotalPaid());
            if (difference < 0.01) {
                return true;
            }
        }

        return false;
    }

    /**
     *  GENERATE BATCH NAME FROM DATE
     */
    private String generateBatchName(LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }
        int year = date.getYear();
        int month = date.getMonthValue();
        String quarter = month <= 3 ? "Q1" : month <= 6 ? "Q2" : month <= 9 ? "Q3" : "Q4";
        return String.format("Batch-%s-%d", quarter, year);
    }

    /**
     *  GET CURRENT LOGGED-IN EMPLOYEE
     */
    private Employee getCurrentEmployee() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof User) {
            User user = (User) authentication.getPrincipal();
            return user.getEmployee();
        }
        throw new RuntimeException("No authenticated user found");
    }

    /**
     *  CONVERT LOG TO DTO
     */
    private ManualCertificateLogDTO convertLogToDTO(ManualCertificateLog log) {
        return ManualCertificateLogDTO.builder()
                .id(log.getId())
                .registrationNo(log.getRegistrationNo())
                .studentName(log.getStudentName())
                .courseName(log.getCourseName())
                .createdByEmployeeName(log.getCreatedByEmployeeName())
                .reason(log.getReason())
                .certificateId(log.getCertificateId())
                .createdAt(log.getCreatedAt())
                .build();
    }

    /**
     * NORMALIZES COURSE LIST
     * - Handles legacy data where multiple courses were stored as a single string
     * - Recognizes "  " (double space) or "," (comma) as separators
     */
    private List<String> normalizeCourseList(List<String> rawCourses) {
        java.util.List<String> normalized = new java.util.ArrayList<>();
        if (rawCourses == null) return normalized;

        for (String entry : rawCourses) {
            if (entry == null || entry.trim().isEmpty()) continue;

            // Check if string contains multiple courses (concatenated with 2+ spaces or comma)
            if (entry.contains("  ") || entry.contains(",")) {
                // Split by 2+ spaces OR comma
                String[] parts = entry.split("\\s{2,}|\\s*,\\s*");
                for (String part : parts) {
                    if (part != null && !part.trim().isEmpty()) {
                        normalized.add(part.trim());
                    }
                }
            } else {
                normalized.add(entry.trim());
            }
        }
        return normalized;
    }

    /**
     * BULK GENERATE CERTIFICATES FOR SELECTED STUDENTS ONLY
     * - Processes only the given registration numbers (not ALL admissions)
     * - Creates separate certificate for each course
     * - Skips duplicates (same regNo + course)
     * - Each student processed independently so one failure doesn't stop the batch
     */
    @Transactional
    public int bulkGenerateCertificates(List<String> registrationNumbers) {
        log.info("[SYNC] Bulk certificate generation for {} student(s)", registrationNumbers.size());

        int certificatesCreated = 0;

        for (String regNo : registrationNumbers) {
            try {
                if (regNo == null || regNo.trim().isEmpty()) {
                    continue;
                }

                regNo = regNo.trim();

                Admission admission = admissionRepository.findByRegistrationNumberAndIsDeletedFalse(regNo);

                if (admission == null) {
                    log.warn("[WARN] Admission not found for regNo: {}", regNo);
                    continue;
                }

                List<String> courses = normalizeCourseList(admission.getCourses());

                if (courses.isEmpty()) {
                    log.warn("[WARN] No courses found for regNo: {}", regNo);
                    continue;
                }

                for (String courseName : courses) {
                    // Skip if certificate already exists for this student + course
                    boolean exists = certificateRepository.existsByRegistrationNoAndCourseNameAndIsActiveTrue(
                            regNo, courseName
                    );

                    if (exists) {
                        log.debug("[SKIP] Certificate already exists for {} - {}", regNo, courseName);
                        continue;
                    }

                    Certificate certificate = createCertificateFromAdmission(admission, courseName);
                    certificateRepository.save(certificate);

                    certificatesCreated++;
                    log.info("[OK] Created certificate for {} - {} ({})",
                            admission.getFullName(), courseName, regNo);
                }

            } catch (Exception e) {
                log.error("[FAIL] Error creating certificate for {}: {}", regNo, e.getMessage());
            }
        }

        log.info("[OK] Bulk generation complete: {} certificates created", certificatesCreated);
        return certificatesCreated;
    }
}
