package com.tts.sms.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "fee_receipts", indexes = {
        @Index(name = "idx_receipt_no", columnList = "receipt_number", unique = true),
        @Index(name = "idx_reg_no", columnList = "registration_number"),
        @Index(name = "idx_receipt_date", columnList = "receipt_date"),
        @Index(name = "idx_payment_mode", columnList = "payment_mode")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeeReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "receipt_number", unique = true, length = 50)
    private String receiptNumber;

    @Column(name = "invoice_number", length = 50)
    private String invoiceNumber;

    @Column(name = "registration_number", nullable = false, length = 50)
    private String registrationNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registration_number", referencedColumnName = "registration_number",
            insertable = false, updatable = false)
    private Admission admission;

    @Column(name = "installment_id")
    private Long installmentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "installment_id", insertable = false, updatable = false)
    private FeeInstallment installment;

    @Column(name = "receipt_date", nullable = false)
    private LocalDate receiptDate;

    @Column(name = "amount_received", nullable = false)
    private Double amountReceived;

    @Column(name = "previous_paid")
    private Double previousPaid;

    @Column(name = "total_fees")
    private Double totalFees;

    @Column(name = "pending_fees")
    private Double pendingFees;

    // GST Fields
    @Column(name = "gst_enabled")
    @Builder.Default
    private Boolean gstEnabled = false;

    @Column(name = "sgst_percent")
    private Double sgstPercent;

    @Column(name = "cgst_percent")
    private Double cgstPercent;

    @Column(name = "invoice_value")
    private Double invoiceValue;

    // Payment Details
    @Column(name = "payment_mode", length = 50, nullable = false)
    @Builder.Default
    private String paymentMode = "Cash";

    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Column(name = "cheque_number", length = 20)
    private String chequeNumber;

    @Column(name = "cheque_date")
    private LocalDate chequeDate;

    @Column(name = "transaction_number", length = 50)
    private String transactionNumber;

    @Column(name = "ifsc_code", length = 15)
    private String ifscCode;

    @Column(name = "online_payment_mode", length = 50)
    private String onlinePaymentMode;

    @Column(name = "next_due_date")
    private LocalDate nextDueDate;

    @Column(name = "receipt_type", length = 50)
    @Builder.Default
    private String receiptType = "Regular";

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "status", length = 50)
    @Builder.Default
    private String status = "Active";

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
        if (receiptDate == null) {
            receiptDate = LocalDate.now();
        }
        if (gstEnabled == null) {
            gstEnabled = false;
        }
        if (paymentMode == null) {
            paymentMode = "Cash";
        }
        if (receiptType == null) {
            receiptType = "Regular";
        }
        if (status == null) {
            status = "Active";
        }
    }
}