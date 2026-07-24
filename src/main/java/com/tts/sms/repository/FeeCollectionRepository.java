package com.tts.sms.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.tts.sms.model.FeeCollection;

@Repository
public interface FeeCollectionRepository extends JpaRepository<FeeCollection, Long> {

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

    @Query("SELECT COALESCE(SUM(fc.paidFees), 0.0) FROM FeeCollection fc WHERE fc.isDeleted = false AND fc.registrationNumber = :regNo")
    Double sumPaidFeesByRegistrationNumber(@Param("regNo") String regNo);

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

    @Query("SELECT fc FROM FeeCollection fc WHERE fc.isDeleted = false " +
            "AND fc.mobileNo = :mobile " +
            "ORDER BY fc.receiptDate DESC")
    List<FeeCollection> findByMobileNoAndIsDeletedFalse(@Param("mobile") String mobile);

    @Query("SELECT fc FROM FeeCollection fc WHERE fc.isDeleted = false " +
            "AND fc.mobileNo IN :mobiles " +
            "ORDER BY fc.receiptDate DESC")
    List<FeeCollection> findByMobileNoInAndIsDeletedFalse(@Param("mobiles") List<String> mobiles);

    @Query("SELECT fc FROM FeeCollection fc WHERE fc.isDeleted = false " +
            "AND (fc.registrationNumber = :regNo OR fc.mobileNo IN :mobiles) " +
            "ORDER BY fc.receiptDate DESC")
    List<FeeCollection> findByRegistrationNumberOrMobileNoInAndIsDeletedFalse(
            @Param("regNo") String regNo,
            @Param("mobiles") List<String> mobiles
    );

    @Query("SELECT fc FROM FeeCollection fc WHERE fc.isDeleted = false " +
            "AND fc.registrationNumber = :regNo " +
            "ORDER BY fc.receiptDate DESC")
    List<FeeCollection> findByRegistrationNumberAndIsDeletedFalse(@Param("regNo") String regNo);

    interface CombinedFeeRow {
        Long getId();

        String getReceiptNo();

        String getStudentName();

        String getMobileNo();

        LocalDate getReceiptDate();

        String getReceiptDateOriginal();

        Double getPaidFees();

        String getPaymentMode();

        String getDataSource();

        String getRegistrationNumber();

        java.time.LocalDateTime getCreatedAt();
    }

    @Query(value = "SELECT * FROM (\n" +
            "    SELECT \n" +
            "        fc.id AS id,\n" +
            "        fc.receipt_no AS receiptNo,\n" +
            "        fc.student_name AS studentName,\n" +
            "        fc.mobile_no AS mobileNo,\n" +
            "        fc.receipt_date AS receiptDate,\n" +
            "        fc.receipt_date_original AS receiptDateOriginal,\n" +
            "        fc.paid_fees AS paidFees,\n" +
            "        fc.payment_mode AS paymentMode,\n" +
            "        fc.data_source AS dataSource,\n" +
            "        fc.registration_number AS registrationNumber,\n" +
            "        fc.created_at AS createdAt\n" +
            "    FROM fee_collections fc\n" +
            "    WHERE fc.is_deleted = false\n" +
            "      AND fc.receipt_date BETWEEN :fromDate AND :toDate\n" +
            "      AND (:paymentMode IS NULL OR fc.payment_mode = :paymentMode)\n" +
            "      AND (:dataSource IS NULL OR :dataSource = 'IMPORTED_OLD_DATA')\n" +
            "\n" +
            "    UNION ALL\n" +
            "\n" +
            "    SELECT \n" +
            "        fr.id AS id,\n" +
            "        fr.receipt_number AS receiptNo,\n" +
            "        CONCAT_WS(' ', a.first_name, a.middle_name, a.last_name) AS studentName,\n" +
            "        a.mobile_primary AS mobileNo,\n" +
            "        fr.receipt_date AS receiptDate,\n" +
            "        NULL AS receiptDateOriginal,\n" +
            "        fr.amount_received AS paidFees,\n" +
            "        fr.payment_mode AS paymentMode,\n" +
            "        'NEW_ENTRY' AS dataSource,\n" +
            "        fr.registration_number AS registrationNumber,\n" +
            "        fr.created_at AS createdAt\n" +
            "    FROM fee_receipts fr\n" +
            "    JOIN admissions a ON a.registration_number = fr.registration_number AND a.is_deleted = false\n" +
            "    WHERE fr.is_deleted = false\n" +
            "      AND fr.receipt_date BETWEEN :fromDate AND :toDate\n" +
            "      AND (:paymentMode IS NULL OR fr.payment_mode = :paymentMode)\n" +
            "      AND (:dataSource IS NULL OR :dataSource = 'NEW_ENTRY')\n" +
            ") AS combined\n" +
            "ORDER BY receiptDate DESC, createdAt DESC\n" +
            "LIMIT :limit OFFSET :offset",
            nativeQuery = true)
    List<CombinedFeeRow> findCombinedFeeCollections(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("dataSource") String dataSource,
            @Param("paymentMode") String paymentMode,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Query(value = "SELECT (\n" +
            "    (SELECT COUNT(*) FROM fee_collections fc\n" +
            "        WHERE fc.is_deleted = false\n" +
            "          AND fc.receipt_date BETWEEN :fromDate AND :toDate\n" +
            "          AND (:paymentMode IS NULL OR fc.payment_mode = :paymentMode)\n" +
            "          AND (:dataSource IS NULL OR :dataSource = 'IMPORTED_OLD_DATA')\n" +
            "    )\n" +
            "    +\n" +
            "    (SELECT COUNT(*) FROM fee_receipts fr\n" +
            "        JOIN admissions a ON a.registration_number = fr.registration_number AND a.is_deleted = false\n" +
            "        WHERE fr.is_deleted = false\n" +
            "          AND fr.receipt_date BETWEEN :fromDate AND :toDate\n" +
            "          AND (:paymentMode IS NULL OR fr.payment_mode = :paymentMode)\n" +
            "          AND (:dataSource IS NULL OR :dataSource = 'NEW_ENTRY')\n" +
            "    )\n" +
            ")",
            nativeQuery = true)
    long countCombinedFeeCollections(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("dataSource") String dataSource,
            @Param("paymentMode") String paymentMode
    );
}