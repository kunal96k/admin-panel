package com.tts.sms.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "fee_installments", indexes = {
        @Index(name = "idx_admission_id", columnList = "admission_id"),
        @Index(name = "idx_due_date", columnList = "due_date"),
        @Index(name = "idx_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeeInstallment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Admission ID is required")
    @Column(name = "admission_id", nullable = false)
    private Long admissionId;

    @NotNull(message = "Installment number is required")
    @Column(name = "installment_number", nullable = false)
    private Integer installmentNumber;

    @NotNull(message = "Due date is required")
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @NotNull(message = "Amount is required")
    @Column(name = "amount", nullable = false)
    private Double amount;

    @NotBlank(message = "Status is required")
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private String status = "Pending";

    @Column(name = "paid_amount")
    private Double paidAmount;

    @Column(name = "paid_date")
    private LocalDate paidDate;

    @Column(name = "payment_mode", length = 50)
    private String paymentMode;

    @Column(name = "transaction_id", length = 100)
    private String transactionId;

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

    @PrePersist
    private void prePersist() {
        if (status == null) {
            status = "Pending";
        }
    }

    // Helper method to check if installment is overdue
    @Transient
    public boolean isOverdue() {
        return "Pending".equals(status) && dueDate.isBefore(LocalDate.now());
    }

    // Helper method to check if installment is paid
    @Transient
    public boolean isPaid() {
        return "Paid".equals(status);
    }
}