package com.tts.sms.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.Subselect;
import org.hibernate.annotations.Synchronize;
import lombok.*;

@Entity
@Subselect("SELECT " +
        "    CONCAT('IMPORTED_OLD_DATA_', fc.id) AS unique_id, " +
        "    fc.id AS original_id, " +
        "    fc.receipt_no AS receipt_no, " +
        "    COALESCE(NULLIF(TRIM(fc.student_name), ''), CONCAT_WS(' ', a.first_name, a.middle_name, a.last_name), 'N/A') AS student_name, " +
        "    COALESCE(NULLIF(TRIM(fc.mobile_no), ''), a.mobile_primary, a.mobile_secondary, 'N/A') AS mobile_no, " +
        "    fc.receipt_date AS receipt_date, " +
        "    fc.receipt_date_original AS receipt_date_original, " +
        "    fc.paid_fees AS paid_fees, " +
        "    fc.payment_mode AS payment_mode, " +
        "    'IMPORTED_OLD_DATA' AS data_source, " +
        "    fc.registration_number AS registration_number, " +
        "    fc.created_at AS created_at " +
        "FROM fee_collections fc " +
        "LEFT JOIN admissions a ON a.registration_number = fc.registration_number AND a.is_deleted = false " +
        "WHERE fc.is_deleted = false " +
        "UNION ALL " +
        "SELECT " +
        "    CONCAT('NEW_ENTRY_', fr.id) AS unique_id, " +
        "    fr.id AS original_id, " +
        "    fr.receipt_number AS receipt_no, " +
        "    COALESCE(NULLIF(TRIM(CONCAT_WS(' ', a.first_name, a.middle_name, a.last_name)), ''), 'N/A') AS student_name, " +
        "    COALESCE(a.mobile_primary, a.mobile_secondary, 'N/A') AS mobile_no, " +
        "    fr.receipt_date AS receipt_date, " +
        "    NULL AS receipt_date_original, " +
        "    fr.amount_received AS paid_fees, " +
        "    fr.payment_mode AS payment_mode, " +
        "    'NEW_ENTRY' AS data_source, " +
        "    fr.registration_number AS registration_number, " +
        "    fr.created_at AS created_at " +
        "FROM fee_receipts fr " +
        "LEFT JOIN admissions a ON a.registration_number = fr.registration_number AND a.is_deleted = false " +
        "WHERE fr.is_deleted = false")
@Synchronize({"fee_collections", "fee_receipts", "admissions"})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CombinedFeeCollection {

    @Id
    @Column(name = "unique_id")
    private String uniqueId;

    @Column(name = "original_id")
    private Long originalId;

    @Column(name = "receipt_no")
    private String receiptNo;

    @Column(name = "student_name")
    private String studentName;

    @Column(name = "mobile_no")
    private String mobileNo;

    @Column(name = "receipt_date")
    private LocalDate receiptDate;

    @Column(name = "receipt_date_original")
    private String receiptDateOriginal;

    @Column(name = "paid_fees")
    private Double paidFees;

    @Column(name = "payment_mode")
    private String paymentMode;

    @Column(name = "data_source")
    private String dataSource;

    @Column(name = "registration_number")
    private String registrationNumber;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
