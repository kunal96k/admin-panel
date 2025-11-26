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
     * Find all non-deleted receipts with pagination
     */
    Page<FeeReceipt> findByIsDeletedFalse(Pageable pageable);

    /**
     * Find receipts by admission ID
     */
    List<FeeReceipt> findByAdmissionIdAndIsDeletedFalseOrderByReceiptDateDesc(Long admissionId);

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
     * Get total amount received for admission
     */
    @Query("SELECT SUM(r.amountReceived) FROM FeeReceipt r " +
            "WHERE r.admissionId = :admissionId AND r.isDeleted = false")
    Double getTotalReceivedByAdmission(@Param("admissionId") Long admissionId);

    /**
     * Get total amount received for date range
     */
    @Query("SELECT SUM(r.amountReceived) FROM FeeReceipt r " +
            "WHERE r.isDeleted = false " +
            "AND r.receiptDate BETWEEN :startDate AND :endDate")
    Double getTotalReceivedByDateRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * Count receipts by admission
     */
    @Query("SELECT COUNT(r) FROM FeeReceipt r " +
            "WHERE r.admissionId = :admissionId AND r.isDeleted = false")
    Long countByAdmission(@Param("admissionId") Long admissionId);

    /**
     * Find max receipt number for prefix
     */
    @Query("SELECT MAX(r.receiptNumber) FROM FeeReceipt r " +
            "WHERE r.receiptNumber LIKE CONCAT(:prefix, '%')")
    String findMaxReceiptNumber(@Param("prefix") String prefix);

    /**
     * Get receipt statistics by payment mode
     */
    @Query("SELECT r.paymentMode, COUNT(r), SUM(r.amountReceived) " +
            "FROM FeeReceipt r WHERE r.isDeleted = false " +
            "GROUP BY r.paymentMode")
    List<Object[]> getReceiptStatsByPaymentMode();

    List<FeeReceipt> findByAdmissionIdInAndIsDeletedFalse(List<Long> admissionIds);

    /**
     * Get recent receipts
     */
    @Query("SELECT r FROM FeeReceipt r WHERE r.isDeleted = false " +
            "ORDER BY r.receiptDate DESC, r.createdAt DESC")
    List<FeeReceipt> findRecentReceipts(Pageable pageable);
}