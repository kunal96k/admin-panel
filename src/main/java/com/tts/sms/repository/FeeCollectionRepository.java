package com.tts.sms.repository;

import com.tts.sms.model.FeeCollection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface FeeCollectionRepository extends JpaRepository<FeeCollection, Long> {

    /**
     * Find all non-deleted records with pagination
     */
    Page<FeeCollection> findByIsDeletedFalse(Pageable pageable);

    /**
     * Find by date range
     */
    @Query("SELECT fc FROM FeeCollection fc WHERE fc.isDeleted = false " +
            "AND fc.receiptDate BETWEEN :fromDate AND :toDate " +
            "ORDER BY fc.receiptDate DESC, fc.createdAt DESC")
    Page<FeeCollection> findByDateRange(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable
    );

    /**
     * Find by date range and payment mode
     */
    @Query("SELECT fc FROM FeeCollection fc WHERE fc.isDeleted = false " +
            "AND fc.receiptDate BETWEEN :fromDate AND :toDate " +
            "AND (:paymentMode IS NULL OR fc.paymentMode = :paymentMode) " +
            "ORDER BY fc.receiptDate DESC, fc.createdAt DESC")
    Page<FeeCollection> findByDateRangeAndPaymentMode(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("paymentMode") String paymentMode,
            Pageable pageable
    );

    /**
     * Find by data source (IMPORTED_OLD_DATA or NEW_ENTRY)
     */
    @Query("SELECT fc FROM FeeCollection fc WHERE fc.isDeleted = false " +
            "AND fc.receiptDate BETWEEN :fromDate AND :toDate " +
            "AND (:dataSource IS NULL OR fc.dataSource = :dataSource) " +
            "AND (:paymentMode IS NULL OR fc.paymentMode = :paymentMode) " +
            "ORDER BY fc.receiptDate DESC, fc.createdAt DESC")
    Page<FeeCollection> findByFilters(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("dataSource") String dataSource,
            @Param("paymentMode") String paymentMode,
            Pageable pageable
    );

    /**
     * Get total amount by date range
     */
    @Query("SELECT COALESCE(SUM(fc.paidFees), 0.0) FROM FeeCollection fc " +
            "WHERE fc.isDeleted = false " +
            "AND fc.receiptDate BETWEEN :fromDate AND :toDate")
    Double getTotalAmountByDateRange(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    /**
     * Get total amount by date range and filters
     */
    @Query("SELECT COALESCE(SUM(fc.paidFees), 0.0) FROM FeeCollection fc " +
            "WHERE fc.isDeleted = false " +
            "AND fc.receiptDate BETWEEN :fromDate AND :toDate " +
            "AND (:dataSource IS NULL OR fc.dataSource = :dataSource) " +
            "AND (:paymentMode IS NULL OR fc.paymentMode = :paymentMode)")
    Double getTotalAmountByFilters(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("dataSource") String dataSource,
            @Param("paymentMode") String paymentMode
    );

    /**
     * Count records by date range and filters
     */
    @Query("SELECT COUNT(fc) FROM FeeCollection fc " +
            "WHERE fc.isDeleted = false " +
            "AND fc.receiptDate BETWEEN :fromDate AND :toDate " +
            "AND (:dataSource IS NULL OR fc.dataSource = :dataSource) " +
            "AND (:paymentMode IS NULL OR fc.paymentMode = :paymentMode)")
    Long countByFilters(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("dataSource") String dataSource,
            @Param("paymentMode") String paymentMode
    );

    /**
     * Find by import batch ID
     */
    List<FeeCollection> findByImportBatchIdAndIsDeletedFalse(String importBatchId);

    /**
     * Get statistics by payment mode
     */
    @Query("SELECT fc.paymentMode, COUNT(fc), SUM(fc.paidFees) " +
            "FROM FeeCollection fc WHERE fc.isDeleted = false " +
            "AND fc.receiptDate BETWEEN :fromDate AND :toDate " +
            "GROUP BY fc.paymentMode")
    List<Object[]> getStatsByPaymentMode(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    /**
     * Get total records count
     */
    @Query("SELECT COUNT(fc) FROM FeeCollection fc WHERE fc.isDeleted = false")
    Long countTotalRecords();

    /**
     * Get today's total amount
     */
    @Query("SELECT COALESCE(SUM(fc.paidFees), 0.0) FROM FeeCollection fc " +
            "WHERE fc.isDeleted = false AND fc.receiptDate = CURRENT_DATE")
    Double getTotalAmountToday();
    /**
     * Find by filters as list (not paginated)
     */
    @Query("SELECT fc FROM FeeCollection fc WHERE fc.isDeleted = false " +
            "AND fc.receiptDate BETWEEN :fromDate AND :toDate " +
            "AND (:dataSource IS NULL OR fc.dataSource = :dataSource) " +
            "AND (:paymentMode IS NULL OR fc.paymentMode = :paymentMode) " +
            "ORDER BY fc.receiptDate DESC, fc.createdAt DESC")
    List<FeeCollection> findByFiltersAsList(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("dataSource") String dataSource,
            @Param("paymentMode") String paymentMode
    );

    /**
     * Find fee collections by student name and mobile (for matching with admission)
     */
    @Query("SELECT fc FROM FeeCollection fc WHERE fc.isDeleted = false " +
            "AND LOWER(fc.studentName) = LOWER(:studentName) " +
            "AND fc.mobileNo = :mobile " +
            "ORDER BY fc.receiptDate DESC")
    List<FeeCollection> findByStudentNameAndMobileAndIsDeletedFalse(
            @Param("studentName") String studentName,
            @Param("mobile") String mobile
    );

    /**
     * Alternative search with partial name match (in case of variations)
     */
    @Query("SELECT fc FROM FeeCollection fc WHERE fc.isDeleted = false " +
            "AND LOWER(fc.studentName) LIKE LOWER(CONCAT('%', :namePart, '%')) " +
            "AND fc.mobileNo = :mobile " +
            "ORDER BY fc.receiptDate DESC")
    List<FeeCollection> findByNamePartAndMobileAndIsDeletedFalse(
            @Param("namePart") String namePart,
            @Param("mobile") String mobile
    );
}