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
     *  : Find refunds by registration number
     */
    List<FeeRefund> findByRegistrationNumberAndIsDeletedFalseOrderByRefundDateDesc(String registrationNumber);

    /**
     * Find refund by refund number
     */
    Optional<FeeRefund> findByRefundNumberAndIsDeletedFalse(String refundNumber);

    /**
     * Find max refund number for generating new refund numbers
     */
    @Query("SELECT MAX(r.refundNumber) FROM FeeRefund r WHERE r.refundNumber LIKE :prefix%")
    String findMaxRefundNumber(@Param("prefix") String prefix);

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
     *  : Get total refund amount by registration number
     */
    @Query("SELECT COALESCE(SUM(r.refundAmount), 0.0) FROM FeeRefund r " +
            "WHERE r.registrationNumber = :registrationNumber AND r.isDeleted = false")
    Double getTotalRefundByRegistrationNumber(@Param("registrationNumber") String registrationNumber);

    /**
     * Get total refund amount for date range
     */
    @Query("SELECT COALESCE(SUM(r.refundAmount), 0.0) FROM FeeRefund r " +
            "WHERE r.isDeleted = false " +
            "AND r.refundDate BETWEEN :startDate AND :endDate")
    Double getTotalRefundByDateRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     *  : Count refunds by registration number
     */
    @Query("SELECT COUNT(r) FROM FeeRefund r " +
            "WHERE r.registrationNumber = :registrationNumber AND r.isDeleted = false")
    Long countByRegistrationNumber(@Param("registrationNumber") String registrationNumber);

    /**
     * Get refund statistics by payment mode
     */
    @Query("SELECT r.paymentMode, COUNT(r), SUM(r.refundAmount) " +
            "FROM FeeRefund r WHERE r.isDeleted = false " +
            "GROUP BY r.paymentMode")
    List<Object[]> getRefundStatsByPaymentMode();

    /**
     *  NEW: Find refunds by multiple registration numbers
     */
    @Query("SELECT r FROM FeeRefund r WHERE r.registrationNumber IN :registrationNumbers " +
            "AND r.isDeleted = false")
    List<FeeRefund> findByRegistrationNumberInAndIsDeletedFalse(
            @Param("registrationNumbers") List<String> registrationNumbers
    );

    /**
     * Get recent refunds
     */
    @Query("SELECT r FROM FeeRefund r WHERE r.isDeleted = false " +
            "ORDER BY r.refundDate DESC, r.createdAt DESC")
    List<FeeRefund> findRecentRefunds(Pageable pageable);

    /**
     * Get total refunds count
     */
    @Query("SELECT COUNT(r) FROM FeeRefund r WHERE r.isDeleted = false")
    Long countTotalRefunds();

    /**
     * Get today's refunds
     */
    @Query("SELECT r FROM FeeRefund r WHERE r.isDeleted = false " +
            "AND r.refundDate = CURRENT_DATE " +
            "ORDER BY r.createdAt DESC")
    List<FeeRefund> findTodaysRefunds();

    /**
     * Get total refund amount today
     */
    @Query("SELECT COALESCE(SUM(r.refundAmount), 0.0) FROM FeeRefund r " +
            "WHERE r.isDeleted = false AND r.refundDate = CURRENT_DATE")
    Double getTotalRefundToday();

    /**
     * Find refunds by payment mode
     */
    List<FeeRefund> findByPaymentModeAndIsDeletedFalse(String paymentMode);

    /**
     * Find refunds pending clearance
     */
    @Query("SELECT r FROM FeeRefund r WHERE r.isDeleted = false " +
            "AND r.paymentClear = false " +
            "ORDER BY r.refundDate ASC")
    List<FeeRefund> findPendingClearance();
}