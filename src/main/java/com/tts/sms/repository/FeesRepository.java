package com.tts.sms.repository;

import com.tts.sms.model.Fees;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FeesRepository extends JpaRepository<Fees, Long>, JpaSpecificationExecutor<Fees> {

    /**
     * Find all non-deleted fees with pagination
     */
    Page<Fees> findByIsDeletedFalse(Pageable pageable);

    /**
     * Find by registration number
     */
    Optional<Fees> findByRegistrationNumberAndIsDeletedFalse(String registrationNumber);

    /**
     * Find by mobile
     */
    List<Fees> findByMobileAndIsDeletedFalse(String mobile);

    /**
     * Find by status
     */
    List<Fees> findByStatusAndIsDeletedFalse(String status);

    /**
     * Search by registration number, name or mobile
     */
    @Query("SELECT f FROM Fees f WHERE f.isDeleted = false AND " +
            "(LOWER(f.registrationNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(f.studentName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "f.mobile LIKE CONCAT('%', :search, '%'))")
    Page<Fees> searchFees(@Param("search") String search, Pageable pageable);

    /**
     * Get total fees statistics
     */
    @Query("SELECT " +
            "COALESCE(SUM(f.totalFees), 0.0), " +
            "COALESCE(SUM(f.totalPaid), 0.0), " +
            "COALESCE(SUM(f.feesDue), 0.0), " +
            "COALESCE(SUM(f.feesRefund), 0.0) " +
            "FROM Fees f WHERE f.isDeleted = false")
    Object[] getFeesStatistics();
}