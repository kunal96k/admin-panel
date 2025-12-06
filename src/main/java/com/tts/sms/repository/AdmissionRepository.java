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
import java.util.Optional;

@Repository
public interface AdmissionRepository extends JpaRepository<Admission, Long> {

    /**
     * Check if admission exists for enquiry ID
     */
    boolean existsByEnquiryIdAndIsDeletedFalse(Long enquiryId);

    /**
     * Check if admission exists for mobile number
     */
    boolean existsByMobilePrimaryAndIsDeletedFalse(String mobilePrimary);

    /**
     * Find admissions by mobile number - Returns ALL matches (allows duplicates)
     */
    @Query("SELECT a FROM Admission a WHERE a.mobilePrimary = :mobile AND a.isDeleted = false ORDER BY a.admissionDate DESC")
    List<Admission> findByMobilePrimaryAndIsDeletedFalse(@Param("mobile") String mobilePrimary);

    /**
     * Find FIRST admission by mobile number (for backward compatibility)
     */
    @Query("SELECT a FROM Admission a WHERE a.mobilePrimary = :mobile AND a.isDeleted = false ORDER BY a.admissionDate DESC")
    Optional<Admission> findFirstByMobilePrimaryAndIsDeletedFalse(@Param("mobile") String mobile);

    /**
     * Find ALL admissions by mobile (allows duplicates)
     */
    List<Admission> findAllByMobilePrimaryAndIsDeletedFalse(String mobilePrimary);

    /**
     * : Find admission by registration number - Use created_at in native query
     */
    @Query(value = "SELECT * FROM admissions a WHERE a.registration_number = :regNo " +
            "AND a.is_deleted = false ORDER BY a.created_at DESC LIMIT 1",
            nativeQuery = true)
    Admission findByRegistrationNumberAndIsDeletedFalse(@Param("regNo") String registrationNumber);

    /**
     * : Find all admissions by registration number - Use created_at in native query
     */
    @Query(value = "SELECT * FROM admissions a WHERE a.registration_number = :regNo " +
            "AND a.is_deleted = false ORDER BY a.created_at DESC",
            nativeQuery = true)
    List<Admission> findAllByRegistrationNumberAndIsDeletedFalse(@Param("regNo") String registrationNumber);

    /**
     * : Advanced search - Use created_at instead of createdAt
     */
    @Query(value = """
        SELECT a.* FROM admissions a
        WHERE a.is_deleted = false
        AND (
            ?1 IS NULL OR 
            LOWER(a.first_name) LIKE LOWER(CONCAT('%', ?2, '%')) OR
            LOWER(a.last_name) LIKE LOWER(CONCAT('%', ?3, '%')) OR
            LOWER(a.mobile_primary) LIKE LOWER(CONCAT('%', ?4, '%')) OR
            LOWER(a.registration_number) LIKE LOWER(CONCAT('%', ?5, '%')) OR
            LOWER(a.email_primary) LIKE LOWER(CONCAT('%', ?6, '%'))
        )
        AND (?7 IS NULL OR a.status = ?8)
        AND (?9 IS NULL OR JSON_CONTAINS(a.courses, JSON_QUOTE(?10)))
        AND (?11 IS NULL OR JSON_CONTAINS(a.batches, JSON_QUOTE(?12)))
        AND (?13 IS NULL OR a.academic_year = ?14)
        AND (?15 IS NULL OR a.admission_date >= ?16)
        AND (?17 IS NULL OR a.admission_date <= ?18)
        ORDER BY a.admission_date DESC, a.created_at DESC
            """,
            countQuery = """
        SELECT COUNT(*) FROM admissions a
        WHERE a.is_deleted = false
        AND (
            ?1 IS NULL OR 
            LOWER(a.first_name) LIKE LOWER(CONCAT('%', ?2, '%')) OR
            LOWER(a.last_name) LIKE LOWER(CONCAT('%', ?3, '%')) OR
            LOWER(a.mobile_primary) LIKE LOWER(CONCAT('%', ?4, '%')) OR
            LOWER(a.registration_number) LIKE LOWER(CONCAT('%', ?5, '%')) OR
            LOWER(a.email_primary) LIKE LOWER(CONCAT('%', ?6, '%'))
        )
        AND (?7 IS NULL OR a.status = ?8)
        AND (?9 IS NULL OR JSON_CONTAINS(a.courses, JSON_QUOTE(?10)))
        AND (?11 IS NULL OR JSON_CONTAINS(a.batches, JSON_QUOTE(?12)))
        AND (?13 IS NULL OR a.academic_year = ?14)
        AND (?15 IS NULL OR a.admission_date >= ?16)
        AND (?17 IS NULL OR a.admission_date <= ?18)
        """,
            nativeQuery = true)
    Page<Admission> advancedSearch(
            String searchTerm1, String searchTerm2, String searchTerm3,
            String searchTerm4, String searchTerm5, String searchTerm6,
            String status1, String status2,
            String course1, String course2,
            String batch1, String batch2,
            String academicYear1, String academicYear2,
            LocalDate admissionDateFrom1, LocalDate admissionDateFrom2,
            LocalDate admissionDateTo1, LocalDate admissionDateTo2,
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
     * Find admissions by course - Using native query with JSON function
     */
    @Query(value = "SELECT * FROM admissions a WHERE a.is_deleted = false " +
            "AND JSON_CONTAINS(a.courses, JSON_QUOTE(:course)) " +
            "ORDER BY a.admission_date DESC",
            nativeQuery = true)
    List<Admission> findByCourse(@Param("course") String course);

    /**
     * Find admissions by batch - Using native query with JSON function
     */
    @Query(value = "SELECT * FROM admissions a WHERE a.is_deleted = false " +
            "AND JSON_CONTAINS(a.batches, JSON_QUOTE(:batch)) " +
            "ORDER BY a.admission_date DESC",
            nativeQuery = true)
    List<Admission> findByBatch(@Param("batch") String batch);

    /**
     * Find admissions by academic year
     */
    @Query("SELECT a FROM Admission a WHERE a.academicYear = :year AND a.isDeleted = false ORDER BY a.admissionDate DESC")
    List<Admission> findByAcademicYearAndIsDeletedFalse(@Param("year") String academicYear);

    /**
     * Count total admissions
     */
    @Query("SELECT COUNT(a) FROM Admission a WHERE a.isDeleted = false")
    Long countTotalAdmissions();

    /**
     * : Find recent admissions - Use created_at
     */
    @Query(value = "SELECT * FROM admissions a WHERE a.is_deleted = false " +
            "ORDER BY a.admission_date DESC, a.created_at DESC",
            nativeQuery = true)
    List<Admission> findRecentAdmissions(Pageable pageable);

    /**
     * : Find all with pagination - Use created_at
     */
    @Query(value = "SELECT * FROM admissions a WHERE a.is_deleted = false " +
            "ORDER BY a.created_at DESC",
            countQuery = "SELECT COUNT(*) FROM admissions a WHERE a.is_deleted = false",
            nativeQuery = true)
    Page<Admission> findByIsDeletedFalse(Pageable pageable);

    /**
     * Find all non-deleted admissions
     */
    List<Admission> findByIsDeletedFalse();

    List<Admission> findAllByOrderByCreatedAtDesc();
}