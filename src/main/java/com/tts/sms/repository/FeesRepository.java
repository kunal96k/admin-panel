package com.tts.sms.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.tts.sms.model.Fees;

@Repository
public interface FeesRepository extends JpaRepository<Fees, Long>, JpaSpecificationExecutor<Fees> {

    Page<Fees> findByIsDeletedFalse(Pageable pageable);

    List<Fees> findByIsDeletedFalse();

    /**
     *  Use native query with LIMIT 1 to avoid NonUniqueResultException
     */
    @Query(value = "SELECT * FROM fees f WHERE f.registration_number = :regNo " +
            "AND f.is_deleted = false ORDER BY f.created_at DESC LIMIT 1",
            nativeQuery = true)
    Optional<Fees> findByRegistrationNumberAndIsDeletedFalse(@Param("regNo") String registrationNumber);

    /**
     * Get all fees records for a registration number (allows duplicates)
     */
    @Query("SELECT f FROM Fees f WHERE f.registrationNumber = :regNo " +
            "AND f.isDeleted = false ORDER BY f.createdAt DESC")
    List<Fees> findAllByRegistrationNumberAndIsDeletedFalse(@Param("regNo") String registrationNumber);

    /**
     * Find fees by registration number and course
     */
    @Query(value = "SELECT * FROM fees f WHERE f.registration_number = :regNo " +
            "AND f.course = :courseName AND f.is_deleted = false " +
            "ORDER BY f.created_at DESC LIMIT 1",
            nativeQuery = true)
    Optional<Fees> findByRegistrationNumberAndCourseAndIsDeletedFalse(
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
            "(f.totalFees = f.totalPaid AND f.totalFees > 0)) " +
            "ORDER BY f.createdAt DESC")
    List<Fees> findClearedFeesByRegistrationNumber(@Param("regNo") String registrationNumber);

    /**
     * Find all fees records that are cleared (paid in full)
     */
    @Query("SELECT f FROM Fees f WHERE f.isDeleted = false AND " +
            "f.registrationNumber LIKE 'REG%' AND " +
            "(LOWER(f.status) = 'clear' OR " +
            "f.feesDue IS NULL OR f.feesDue <= 0.01 OR " +
            "ABS(f.totalFees - f.totalPaid) < 0.01) " +
            "ORDER BY f.createdAt DESC")
    List<Fees> findClearedFees();

    @Query("SELECT f FROM Fees f WHERE f.isDeleted = false AND LOWER(f.status) = 'clear' ORDER BY f.createdAt DESC")
    List<Fees> findFeesWithStatusClear();

    /**
     * Find cleared fees for specific student/course combination
     */
    @Query(value = "SELECT * FROM fees f WHERE f.is_deleted = false AND " +
            "f.registration_number = :regNo AND f.course = :course AND " +
            "f.registration_number LIKE 'REG%' AND " +
            "(LOWER(f.status) = 'clear' OR " +
            "f.fees_due IS NULL OR f.fees_due <= 0.01 OR " +
            "ABS(f.total_fees - f.total_paid) < 0.01) " +
            "ORDER BY f.created_at DESC LIMIT 1",
            nativeQuery = true)
    Optional<Fees> findClearedFeesByRegistrationAndCourse(
            @Param("regNo") String regNo,
            @Param("course") String course
    );

    /**
     * Count total cleared fees
     */
    @Query("SELECT COUNT(f) FROM Fees f WHERE f.isDeleted = false AND " +
            "f.registrationNumber LIKE 'REG%' AND " +
            "(LOWER(f.status) = 'clear' OR " +
            "f.feesDue IS NULL OR f.feesDue <= 0.01 OR " +
            "ABS(f.totalFees - f.totalPaid) < 0.01)")
    long countClearedFees();

    /**
 Get revenue from Fees table grouped by month/year
     * This aggregates total_paid by created_at date for ALL historical data
     */
    @Query(value = "SELECT DATE(f.created_at) as payment_date, SUM(f.total_paid) as total_revenue " +
            "FROM fees f " +
            "WHERE f.is_deleted = false " +
            "AND f.created_at BETWEEN :startDate AND :endDate " +
            "AND f.total_paid > 0 " +
            "GROUP BY DATE(f.created_at) " +
            "ORDER BY payment_date ASC",
            nativeQuery = true)
    List<Object[]> getRevenueByDateRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
 Get total revenue for a specific period
     */
    @Query(value = "SELECT COALESCE(SUM(f.total_paid), 0) " +
            "FROM fees f " +
            "WHERE f.is_deleted = false " +
            "AND f.created_at BETWEEN :startDate AND :endDate",
            nativeQuery = true)
    Double getTotalRevenueByDateRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     *  Get all fees records created from a specific date onwards
     */
    @Query("SELECT f FROM Fees f WHERE f.isDeleted = false " +
            "AND f.createdAt >= :fromDate " +
            "ORDER BY f.createdAt DESC")
    List<Fees> findByCreatedAtAfter(@Param("fromDate") LocalDate fromDate);

    /**
     *  Get total collected from cutoff date
     */
    @Query("SELECT COALESCE(SUM(f.totalPaid), 0.0) FROM Fees f " +
            "WHERE f.isDeleted = false " +
            "AND f.createdAt >= :fromDate")
    Double getTotalCollectedFromDate(@Param("fromDate") LocalDate fromDate);

    /**
     *  Get total pending from cutoff date
     */
    @Query("SELECT COALESCE(SUM(f.feesDue), 0.0) FROM Fees f " +
            "WHERE f.isDeleted = false " +
            "AND f.createdAt >= :fromDate " +
            "AND f.feesDue > 0")
    Double getTotalPendingFromDate(@Param("fromDate") LocalDate fromDate);

    /**
     * Batch fetch fees for multiple registration numbers
     */
    @Query("SELECT f FROM Fees f WHERE f.registrationNumber IN :regNos AND f.isDeleted = false")
    List<Fees> findAllByRegistrationNumberInAndIsDeletedFalse(@Param("regNos") List<String> registrationNumbers);
}