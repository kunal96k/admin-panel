package com.tts.sms.repository;

import com.tts.sms.model.Admission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface AdmissionRepository extends JpaRepository<Admission, Long> {

    /**
     * Find all non-deleted admissions with pagination
     */
    Page<Admission> findByIsDeletedFalse(Pageable pageable);

    /**
     * Check if admission exists for enquiry ID
     */
    boolean existsByEnquiryIdAndIsDeletedFalse(Long enquiryId);

    /**
     * Check if admission exists for mobile number
     */
    boolean existsByMobilePrimaryAndIsDeletedFalse(String mobilePrimary);

    /**
     * Find admission by mobile number
     */
    List<Admission> findByMobilePrimaryAndIsDeletedFalse(String mobilePrimary);

    /**
     * Find admission by registration number
     */
    Admission findByRegistrationNumberAndIsDeletedFalse(String registrationNumber);

    /**
     * Advanced search with multiple criteria - FIXED for JSON arrays using native query
     */
    @Query(value = "SELECT a.* FROM admissions a WHERE a.is_deleted = false " +
            "AND (:searchTerm IS NULL OR " +
            "LOWER(a.first_name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(a.last_name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(a.mobile_primary) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(a.registration_number) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(a.email_primary) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
            "AND (:status IS NULL OR a.status = :status) " +
            "AND (:course IS NULL OR JSON_CONTAINS(a.courses, JSON_QUOTE(:course))) " +
            "AND (:batch IS NULL OR JSON_CONTAINS(a.batches, JSON_QUOTE(:batch))) " +
            "AND (:academicYear IS NULL OR a.academic_year = :academicYear) " +
            "AND (:admissionDateFrom IS NULL OR a.admission_date >= :admissionDateFrom) " +
            "AND (:admissionDateTo IS NULL OR a.admission_date <= :admissionDateTo) " +
            "ORDER BY a.admission_date DESC",
            countQuery = "SELECT COUNT(*) FROM admissions a WHERE a.is_deleted = false " +
                    "AND (:searchTerm IS NULL OR " +
                    "LOWER(a.first_name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
                    "LOWER(a.last_name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
                    "LOWER(a.mobile_primary) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
                    "LOWER(a.registration_number) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
                    "LOWER(a.email_primary) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
                    "AND (:status IS NULL OR a.status = :status) " +
                    "AND (:course IS NULL OR JSON_CONTAINS(a.courses, JSON_QUOTE(:course))) " +
                    "AND (:batch IS NULL OR JSON_CONTAINS(a.batches, JSON_QUOTE(:batch))) " +
                    "AND (:academicYear IS NULL OR a.academic_year = :academicYear) " +
                    "AND (:admissionDateFrom IS NULL OR a.admission_date >= :admissionDateFrom) " +
                    "AND (:admissionDateTo IS NULL OR a.admission_date <= :admissionDateTo)",
            nativeQuery = true)
    Page<Admission> advancedSearch(
            @Param("searchTerm") String searchTerm,
            @Param("status") String status,
            @Param("course") String course,
            @Param("batch") String batch,
            @Param("academicYear") String academicYear,
            @Param("admissionDateFrom") LocalDate admissionDateFrom,
            @Param("admissionDateTo") LocalDate admissionDateTo,
            Pageable pageable
    );

    /**
     * Find max registration number for prefix
     */
    @Query("SELECT MAX(a.registrationNumber) FROM Admission a " +
            "WHERE a.registrationNumber LIKE CONCAT(:prefix, '%')")
    String findMaxRegistrationNumber(@Param("prefix") String prefix);

    /**
     * Get admission statistics by status
     */
    @Query("SELECT a.status, COUNT(a) FROM Admission a " +
            "WHERE a.isDeleted = false GROUP BY a.status")
    List<Object[]> getAdmissionStatsByStatus();

    /**
     * Get admission statistics by academic year
     */
    @Query("SELECT a.academicYear, COUNT(a) FROM Admission a " +
            "WHERE a.isDeleted = false GROUP BY a.academicYear")
    List<Object[]> getAdmissionStatsByYear();

    /**
     * Find admissions by course - FIXED using native query with JSON function
     */
    @Query(value = "SELECT * FROM admissions a WHERE a.is_deleted = false " +
            "AND JSON_CONTAINS(a.courses, JSON_QUOTE(:course))",
            nativeQuery = true)
    List<Admission> findByCourse(@Param("course") String course);

    /**
     * Find admissions by batch - FIXED using native query with JSON function
     */
    @Query(value = "SELECT * FROM admissions a WHERE a.is_deleted = false " +
            "AND JSON_CONTAINS(a.batches, JSON_QUOTE(:batch))",
            nativeQuery = true)
    List<Admission> findByBatch(@Param("batch") String batch);

    /**
     * Find admissions by academic year
     */
    List<Admission> findByAcademicYearAndIsDeletedFalse(String academicYear);

    /**
     * Count total admissions
     */
    @Query("SELECT COUNT(a) FROM Admission a WHERE a.isDeleted = false")
    Long countTotalAdmissions();

    /**
     * Find recent admissions
     */
    @Query("SELECT a FROM Admission a WHERE a.isDeleted = false " +
            "ORDER BY a.admissionDate DESC")
    List<Admission> findRecentAdmissions(Pageable pageable);
}