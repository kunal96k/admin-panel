package com.tts.sms.repository;

import com.tts.sms.model.Fees;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FeesRepository extends JpaRepository<Fees, Long> {

    Page<Fees> findByIsDeletedFalse(Pageable pageable);

    List<Fees> findByIsDeletedFalse();

    Optional<Fees> findByRegistrationNumberAndIsDeletedFalse(String registrationNumber);

    /**
     * Find fees by registration number and course
     */
    @Query("SELECT f FROM Fees f WHERE f.registrationNumber = :regNo " +
            "AND f.course = :courseName AND f.isDeleted = false")
    Fees findByRegistrationNumberAndCourseAndIsDeletedFalse(
            @Param("regNo") String registrationNumber,
            @Param("courseName") String courseName
    );

    /**
     * Find cleared fees by registration number
     */
    @Query("SELECT f FROM Fees f WHERE f.isDeleted = false AND " +
            "f.registrationNumber = :regNo AND " +
            "(LOWER(f.status) = 'clear' OR " +
            "f.feesDue = 0 OR " +
            "f.feesDue IS NULL OR " +
            "(f.totalFees = f.totalPaid AND f.totalFees > 0))")
    List<Fees> findClearedFeesByRegistrationNumber(@Param("regNo") String registrationNumber);

    // ==================== NEW METHODS FOR AUTO-CERTIFICATE ====================

    /**
     * ✅ Find all fees records that are cleared (paid in full)
     * Checks multiple conditions:
     * 1. Status = "Clear"
     * 2. Fees Due = 0 or NULL
     * 3. Total Fees = Total Paid
     *
     * ✅ ONLY RETURNS NEW ADMISSIONS (regNo starts with "REG")
     */
    @Query("SELECT f FROM Fees f WHERE f.isDeleted = false AND " +
            "f.registrationNumber LIKE 'REG%' AND " +
            "(LOWER(f.status) = 'clear' OR " +
            "f.feesDue IS NULL OR f.feesDue <= 0.01 OR " +
            "ABS(f.totalFees - f.totalPaid) < 0.01)")
    List<Fees> findClearedFees();

    /**
     * ✅ Find cleared fees for specific student/course combination
     * Used for manual certificate generation
     */
    @Query("SELECT f FROM Fees f WHERE f.isDeleted = false AND " +
            "f.registrationNumber = :regNo AND f.course = :course AND " +
            "f.registrationNumber LIKE 'REG%' AND " +
            "(LOWER(f.status) = 'clear' OR " +
            "f.feesDue IS NULL OR f.feesDue <= 0.01 OR " +
            "ABS(f.totalFees - f.totalPaid) < 0.01)")
    Fees findClearedFeesByRegistrationAndCourse(
            @Param("regNo") String regNo,
            @Param("course") String course
    );

    /**
     * ✅ Count total cleared fees (NEW admissions only)
     */
    @Query("SELECT COUNT(f) FROM Fees f WHERE f.isDeleted = false AND " +
            "f.registrationNumber LIKE 'REG%' AND " +
            "(LOWER(f.status) = 'clear' OR " +
            "f.feesDue IS NULL OR f.feesDue <= 0.01 OR " +
            "ABS(f.totalFees - f.totalPaid) < 0.01)")
    long countClearedFees();

}