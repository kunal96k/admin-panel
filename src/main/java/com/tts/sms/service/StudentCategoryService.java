package com.tts.sms.service;

import com.tts.sms.model.Admission;
import com.tts.sms.model.Certificate;
import com.tts.sms.model.FeeRefund;
import com.tts.sms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentCategoryService {

    private final AdmissionRepository admissionRepository;
    private final CertificateRepository certificateRepository;
    private final FeeRefundRepository feeRefundRepository;
    private final SystemConfigurationService configService;

    /**
     * Recategorize ALL students in the system
     * Called when cutoff date is changed
     */
    @Transactional
    public void recategorizeAllStudents() {
        log.info("🔄 Starting mass recategorization...");

        LocalDate cutoffDate = configService.getCutoffDate();
        log.info("📅 Using cutoff date: {}", cutoffDate);

        List<Admission> allAdmissions = admissionRepository.findByIsDeletedFalse();
        log.info("📊 Total admissions to process: {}", allAdmissions.size());

        int updated = 0;
        int cancelled = 0;
        int completed = 0;
        int newStudent = 0;
        int oldStudent = 0;
        int pursuing = 0;

        for (Admission admission : allAdmissions) {
            String oldCategory = admission.getStudentCategory();
            String newCategory = determineCategory(admission, cutoffDate);

            // Count by category
            switch (newCategory) {
                case "CANCELLED": cancelled++; break;
                case "COMPLETED": completed++; break;
                case "NEW_STUDENT": newStudent++; break;
                case "OLD_STUDENT": oldStudent++; break;
                case "PURSUING": pursuing++; break;
            }

            if (!newCategory.equals(oldCategory)) {
                admission.setStudentCategory(newCategory);
                admission.setCategoryUpdatedAt(LocalDateTime.now());
                admissionRepository.save(admission);
                updated++;

                log.debug(" Updated: {} ({}) - {} → {} (Admission Date: {})",
                        admission.getRegistrationNumber(),
                        admission.getFullName(),
                        oldCategory,
                        newCategory,
                        admission.getAdmissionDate());
            }
        }

        log.info(" Recategorization complete: {} admissions updated", updated);
        log.info("📊 Final counts - CANCELLED: {}, COMPLETED: {}, NEW_STUDENT: {}, OLD_STUDENT: {}, PURSUING: {}",
                cancelled, completed, newStudent, oldStudent, pursuing);
    }

    /**
     * Determine student category based on ADMISSION DATE
     * Priority order:
     * 1. CANCELLED - if has refund
     * 2. COMPLETED - if all certificates issued for ALL enrolled courses
     * 3. NEW_STUDENT - if REG* number
     * 4. OLD_STUDENT - if admission date < cutoff
     * 5. PURSUING - default (admission date >= cutoff)
     */
    @Transactional
    public String determineCategory(Admission admission, LocalDate cutoffDate) {
        String regNo = admission.getRegistrationNumber();
        LocalDate admissionDate = admission.getAdmissionDate();

        // PRIORITY 1: Check if cancelled (has ANY refund)
        if (hasAnyRefund(regNo)) {
            log.debug("✅ {} -> CANCELLED (has refund)", regNo);
            return "CANCELLED";
        }

        // PRIORITY 2: Check if completed (ALL certificates issued for ALL courses)
        if (allCoursesHaveCertificates(admission)) {
            log.debug("✅ {} -> COMPLETED (all certificates issued)", regNo);
            return "COMPLETED";
        }

        // PRIORITY 3: Check if NEW_STUDENT (REG* number)
        if (regNo != null && regNo.startsWith("REG")) {
            log.debug("✅ {} -> NEW_STUDENT (REG number)", regNo);
            return "NEW_STUDENT";
        }

        // PRIORITY 4 & 5: Use ADMISSION DATE (not created/updated dates)
        if (admissionDate != null && admissionDate.isBefore(cutoffDate)) {
            log.debug("✅ {} -> OLD_STUDENT (before cutoff)", regNo);
            return "OLD_STUDENT";
        }

        // Default: PURSUING (admission date >= cutoff OR no admission date)
        log.debug("✅ {} -> PURSUING (default)", regNo);
        return "PURSUING";
    }

    /**
     * Check if student has ANY refund (even ₹0.01 triggers CANCELLED)
     */
    private boolean hasAnyRefund(String regNo) {
        if (regNo == null) return false;

        try {
            List<FeeRefund> refunds = feeRefundRepository
                    .findByRegistrationNumberAndIsDeletedFalseOrderByRefundDateDesc(regNo);

            boolean hasRefund = !refunds.isEmpty();

            if (hasRefund) {
                double totalRefund = refunds.stream()
                        .mapToDouble(r -> r.getRefundAmount() != null ? r.getRefundAmount() : 0.0)
                        .sum();
                log.info("💰 {} has {} refund(s) totaling ₹{}", regNo, refunds.size(), totalRefund);
            }

            return hasRefund;
        } catch (Exception e) {
            log.error("❌ Error checking refunds for {}: {}", regNo, e.getMessage());
            return false;
        }
    }

    /**
     * Check if ALL enrolled courses have issued certificates
     * - Must have AT LEAST ONE certificate per enrolled course
     * - Certificate status must be "Issued"
     */
    private boolean allCoursesHaveCertificates(Admission admission) {
        if (admission.getCourses() == null || admission.getCourses().isEmpty()) {
            log.debug("⏭️ {} has no courses enrolled", admission.getRegistrationNumber());
            return false;
        }

        List<String> enrolledCourses = admission.getCourses();

        try {
            // Get ALL issued certificates for this student
            List<Certificate> issuedCerts = certificateRepository
                    .findByRegistrationNoAndStatusAndIsActiveTrue(
                            admission.getRegistrationNumber(),
                            "Issued"
                    );

            if (issuedCerts.isEmpty()) {
                log.debug("⏭️ {} has no issued certificates", admission.getRegistrationNumber());
                return false;
            }

            // Check EACH enrolled course has at least ONE issued certificate
            for (String courseName : enrolledCourses) {
                boolean hasCertForCourse = issuedCerts.stream()
                        .anyMatch(cert -> cert.getCourseName().equalsIgnoreCase(courseName.trim()));

                if (!hasCertForCourse) {
                    log.debug("⏭️ {} missing certificate for course: {}",
                            admission.getRegistrationNumber(), courseName);
                    return false;
                }
            }

            log.info("✅ {} has ALL certificates issued ({} courses)",
                    admission.getRegistrationNumber(), enrolledCourses.size());
            return true;

        } catch (Exception e) {
            log.error("❌ Error checking certificates for {}: {}",
                    admission.getRegistrationNumber(), e.getMessage());
            return false;
        }
    }

    /**
     * Check if student has any refund
     */
    private boolean hasRefund(String regNo) {
        if (regNo == null) return false;

        List<FeeRefund> refunds = feeRefundRepository
                .findByRegistrationNumberAndIsDeletedFalseOrderByRefundDateDesc(regNo);
        return !refunds.isEmpty();
    }

    /**
     * Check if ALL enrolled courses have issued certificates
     */
    private boolean allCoursesCompleted(Admission admission) {
        if (admission.getCourses() == null || admission.getCourses().isEmpty()) {
            return false;
        }

        List<String> enrolledCourses = admission.getCourses();
        List<Certificate> issuedCertificates = certificateRepository
                .findByRegistrationNoAndStatusAndIsActiveTrue(
                        admission.getRegistrationNumber(),
                        "Issued"
                );

        // If no certificates issued, not completed
        if (issuedCertificates.isEmpty()) {
            return false;
        }

        // Check if ALL enrolled courses have issued certificates
        for (String course : enrolledCourses) {
            boolean hasIssuedCert = issuedCertificates.stream()
                    .anyMatch(cert -> cert.getCourseName().equalsIgnoreCase(course));
            if (!hasIssuedCert) {
                return false; // At least one course not completed
            }
        }

        return true; // All courses have issued certificates
    }

    /**
     * Get category for specific admission
     */
    @Transactional(readOnly = true)
    public String getCategoryForAdmission(Long admissionId) {
        Admission admission = admissionRepository.findById(admissionId)
                .orElseThrow(() -> new RuntimeException("Admission not found"));

        LocalDate cutoffDate = configService.getCutoffDate();
        return determineCategory(admission, cutoffDate);
    }
}