package com.tts.sms.repository;

import com.tts.sms.model.FeeReceipt;
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
public interface FeeReceiptRepository extends JpaRepository<FeeReceipt, Long> {

    /**
     * Find receipts by date range and payment mode for fee collection display
     */
    @Query("SELECT r FROM FeeReceipt r WHERE r.isDeleted = false " +
            "AND r.receiptDate BETWEEN :startDate AND :endDate " +
            "AND (:paymentMode IS NULL OR r.paymentMode = :paymentMode) " +
            "ORDER BY r.receiptDate DESC")
    List<FeeReceipt> findByFiltersForCollection(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("paymentMode") String paymentMode
    );

    /**
     * Count receipts by date range and payment mode
     */
    @Query("SELECT COUNT(r) FROM FeeReceipt r WHERE r.isDeleted = false " +
            "AND r.receiptDate BETWEEN :startDate AND :endDate " +
            "AND (:paymentMode IS NULL OR r.paymentMode = :paymentMode)")
    Long countByDateRangeAndPaymentMode(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("paymentMode") String paymentMode
    );

    /**
     * Get total amount by date range and payment mode
     */
    @Query("SELECT COALESCE(SUM(r.amountReceived), 0.0) FROM FeeReceipt r " +
            "WHERE r.isDeleted = false " +
            "AND r.receiptDate BETWEEN :startDate AND :endDate " +
            "AND (:paymentMode IS NULL OR r.paymentMode = :paymentMode)")
    Double getTotalReceivedByDateRangeAndPaymentMode(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("paymentMode") String paymentMode
    );

    /**
     * Find all non-deleted receipts with pagination
     */
    Page<FeeReceipt> findByIsDeletedFalse(Pageable pageable);

    /**
     *  Find receipts by registration number
     */
    List<FeeReceipt> findByRegistrationNumberAndIsDeletedFalseOrderByReceiptDateDesc(String registrationNumber);

    /**
     * Find max receipt number for generating new receipt numbers
     */
    @Query("SELECT MAX(r.receiptNumber) FROM FeeReceipt r WHERE r.receiptNumber LIKE :prefix%")
    String findMaxReceiptNumber(@Param("prefix") String prefix);

    /**
     * Find receipt by receipt number
     */
    Optional<FeeReceipt> findByReceiptNumberAndIsDeletedFalse(String receiptNumber);

    /**
     * Find receipts by installment ID
     */
    List<FeeReceipt> findByInstallmentIdAndIsDeletedFalse(Long installmentId);

    /**
     * Find receipts by payment mode
     */
    List<FeeReceipt> findByPaymentModeAndIsDeletedFalse(String paymentMode);

    /**
     * Find receipts by date range
     */
    @Query("SELECT r FROM FeeReceipt r WHERE r.isDeleted = false " +
            "AND r.receiptDate BETWEEN :startDate AND :endDate " +
            "ORDER BY r.receiptDate DESC")
    List<FeeReceipt> findByDateRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     *  Get total amount received by registration number
     */
    @Query("SELECT COALESCE(SUM(r.amountReceived), 0.0) FROM FeeReceipt r " +
            "WHERE r.registrationNumber = :registrationNumber AND r.isDeleted = false")
    Double getTotalReceivedByRegistrationNumber(@Param("registrationNumber") String registrationNumber);

    /**
     * Get total amount received for date range
     */
    @Query("SELECT COALESCE(SUM(r.amountReceived), 0.0) FROM FeeReceipt r " +
            "WHERE r.isDeleted = false " +
            "AND r.receiptDate BETWEEN :startDate AND :endDate")
    Double getTotalReceivedByDateRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     *  Count receipts by registration number
     */
    @Query("SELECT COUNT(r) FROM FeeReceipt r " +
            "WHERE r.registrationNumber = :registrationNumber AND r.isDeleted = false")
    Long countByRegistrationNumber(@Param("registrationNumber") String registrationNumber);

    /**
     * Get receipt statistics by payment mode
     */
    @Query("SELECT r.paymentMode, COUNT(r), SUM(r.amountReceived) " +
            "FROM FeeReceipt r WHERE r.isDeleted = false " +
            "GROUP BY r.paymentMode")
    List<Object[]> getReceiptStatsByPaymentMode();

    /**
     *  Find receipts by multiple registration numbers
     */
    @Query("SELECT r FROM FeeReceipt r WHERE r.registrationNumber IN :registrationNumbers " +
            "AND r.isDeleted = false")
    List<FeeReceipt> findByRegistrationNumberInAndIsDeletedFalse(
            @Param("registrationNumbers") List<String> registrationNumbers
    );

    /**
     * Get recent receipts
     */
    @Query("SELECT r FROM FeeReceipt r WHERE r.isDeleted = false " +
            "ORDER BY r.receiptDate DESC, r.createdAt DESC")
    List<FeeReceipt> findRecentReceipts(Pageable pageable);

    /**
     * Get total receipts count
     */
    @Query("SELECT COUNT(r) FROM FeeReceipt r WHERE r.isDeleted = false")
    Long countTotalReceipts();

    /**
     * Get today's receipts
     */
    @Query("SELECT r FROM FeeReceipt r WHERE r.isDeleted = false " +
            "AND r.receiptDate = CURRENT_DATE " +
            "ORDER BY r.createdAt DESC")
    List<FeeReceipt> findTodaysReceipts();

    /**
     * Get total amount received today
     */
    @Query("SELECT COALESCE(SUM(r.amountReceived), 0.0) FROM FeeReceipt r " +
            "WHERE r.isDeleted = false AND r.receiptDate = CURRENT_DATE")
    Double getTotalReceivedToday();
}