package com.tts.sms.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "fee_refunds", indexes = {
        @Index(name = "idx_refund_no", columnList = "refund_number", unique = true),
        @Index(name = "idx_refund_reg_no", columnList = "registration_number"),
        @Index(name = "idx_refund_date", columnList = "refund_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeeRefund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "refund_number", unique = true, length = 50)
    private String refundNumber;

    @Column(name = "registration_number", nullable = false, length = 50)
    private String registrationNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registration_number", referencedColumnName = "registration_number",
            insertable = false, updatable = false)
    private Admission admission;

    @Column(name = "refund_date", nullable = false)
    private LocalDate refundDate;

    @Column(name = "refund_amount", nullable = false)
    private Double refundAmount;

    @Column(name = "total_fees")
    private Double totalFees;

    @Column(name = "paid_fees")
    private Double paidFees;

    @Column(name = "pending_fees")
    private Double pendingFees;

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

    @Column(name = "payment_clear")
    @Builder.Default
    private Boolean paymentClear = false;

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
        if (refundDate == null) {
            refundDate = LocalDate.now();
        }
        if (paymentMode == null) {
            paymentMode = "Cash";
        }
        if (paymentClear == null) {
            paymentClear = false;
        }
        if (status == null) {
            status = "Active";
        }
    }
}