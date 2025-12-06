package com.tts.sms.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Separate model for Fee Collection records
 * Allows duplicate receipts and imports data AS-IS without validation
 */
@Entity
@Table(name = "fee_collections", indexes = {
        @Index(name = "idx_fc_receipt_no", columnList = "receipt_no"),
        @Index(name = "idx_fc_student_name", columnList = "student_name"),
        @Index(name = "idx_fc_mobile", columnList = "mobile_no"),
        @Index(name = "idx_fc_receipt_date", columnList = "receipt_date"),
        @Index(name = "idx_fc_payment_mode", columnList = "payment_mode"),
        @Index(name = "idx_fc_data_source", columnList = "data_source")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeeCollection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Receipt number (allows duplicates for old data)
     */
    @Column(name = "receipt_no", length = 100)
    private String receiptNo;

    /**
     * Student name (stored as-is from import)
     */
    @Column(name = "student_name", length = 500)
    private String studentName;

    /**
     * Mobile number (stored as-is, can be null or invalid)
     */
    @Column(name = "mobile_no", length = 50)
    private String mobileNo;

    /**
     * Receipt date (stored as-is from CSV)
     */
    @Column(name = "receipt_date")
    private LocalDate receiptDate;

    /**
     * Original receipt date string from CSV (for reference)
     */
    @Column(name = "receipt_date_original", length = 100)
    private String receiptDateOriginal;

    /**
     * Paid fees amount
     */
    @Column(name = "paid_fees")
    private Double paidFees;

    /**
     * Payment mode (Cash, Online, Cheque, etc.)
     */
    @Column(name = "payment_mode", length = 100)
    private String paymentMode;

    /**
     * Data source: IMPORTED_OLD_DATA or NEW_ENTRY
     */
    @Column(name = "data_source", length = 50)
    @Builder.Default
    private String dataSource = "NEW_ENTRY";

    /**
     * Registration number (if linked to admission)
     */
    @Column(name = "registration_number", length = 100)
    private String registrationNumber;

    /**
     * Import batch ID for tracking bulk imports
     */
    @Column(name = "import_batch_id", length = 100)
    private String importBatchId;

    /**
     * Notes or remarks
     */
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // Metadata
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    private void prePersist() {
        if (dataSource == null) {
            dataSource = "NEW_ENTRY";
        }
        if (isDeleted == null) {
            isDeleted = false;
        }
    }
}