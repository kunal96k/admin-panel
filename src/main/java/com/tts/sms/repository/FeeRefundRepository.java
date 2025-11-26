package com.tts.sms.repository;

import com.tts.sms.model.FeeRefund;
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
public interface FeeRefundRepository extends JpaRepository<FeeRefund, Long> {

    /**
     * Find all non-deleted refunds with pagination
     */
    Page<FeeRefund> findByIsDeletedFalse(Pageable pageable);

    /**
     * Find refunds by admission ID
     */
    List<FeeRefund> findByAdmissionIdAndIsDeletedFalseOrderByRefundDateDesc(Long admissionId);

    /**
     * Find refund by refund number
     */
    Optional<FeeRefund> findByRefundNumberAndIsDeletedFalse(String refundNumber);

    /**
     * Find refunds by date range
     */
    @Query("SELECT r FROM FeeRefund r WHERE r.isDeleted = false " +
            "AND r.refundDate BETWEEN :startDate AND :endDate " +
            "ORDER BY r.refundDate DESC")
    List<FeeRefund> findByDateRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * Get total refund amount for admission
     */
    @Query("SELECT SUM(r.refundAmount) FROM FeeRefund r " +
            "WHERE r.admissionId = :admissionId AND r.isDeleted = false")
    Double getTotalRefundByAdmission(@Param("admissionId") Long admissionId);

    /**
     * Get total refund amount for date range
     */
    @Query("SELECT SUM(r.refundAmount) FROM FeeRefund r " +
            "WHERE r.isDeleted = false " +
            "AND r.refundDate BETWEEN :startDate AND :endDate")
    Double getTotalRefundByDateRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * Count refunds by admission
     */
    @Query("SELECT COUNT(r) FROM FeeRefund r " +
            "WHERE r.admissionId = :admissionId AND r.isDeleted = false")
    Long countByAdmission(@Param("admissionId") Long admissionId);

    /**
     * Find max refund number for prefix
     */
    @Query("SELECT MAX(r.refundNumber) FROM FeeRefund r " +
            "WHERE r.refundNumber LIKE CONCAT(:prefix, '%')")
    String findMaxRefundNumber(@Param("prefix") String prefix);

    /**
     * Get refund statistics by payment mode
     */
    @Query("SELECT r.paymentMode, COUNT(r), SUM(r.refundAmount) " +
            "FROM FeeRefund r WHERE r.isDeleted = false " +
            "GROUP BY r.paymentMode")
    List<Object[]> getRefundStatsByPaymentMode();

    List<FeeRefund> findByAdmissionIdInAndIsDeletedFalse(List<Long> admissionIds);

    /**
     * Get recent refunds
     */
    @Query("SELECT r FROM FeeRefund r WHERE r.isDeleted = false " +
            "ORDER BY r.refundDate DESC, r.createdAt DESC")
    List<FeeRefund> findRecentRefunds(Pageable pageable);
}