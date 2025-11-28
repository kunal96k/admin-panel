package com.tts.sms.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "fees", indexes = {
        @Index(name = "idx_fees_reg_no", columnList = "registration_number"),
        @Index(name = "idx_fees_mobile", columnList = "mobile"),
        @Index(name = "idx_fees_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Fees {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "registration_number", nullable = false, length = 50)
    private String registrationNumber;

    @Column(name = "student_name", nullable = false, length = 200)
    private String studentName;

    @Column(name = "mobile", length = 15)
    private String mobile;

    @Column(name = "total_fees")
    @Builder.Default
    private Double totalFees = 0.0;

    @Column(name = "fees_due")
    @Builder.Default
    private Double feesDue = 0.0;

    @Column(name = "total_paid")
    @Builder.Default
    private Double totalPaid = 0.0;

    // ALLOW NULL - DO NOT SET CURRENT DATE
    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "fees_refund")
    @Builder.Default
    private Double feesRefund = 0.0;

    @Column(name = "status", length = 50)
    @Builder.Default
    private String status = "Pending";

    @Column(name = "course", length = 200)
    private String course;

    // Keep admission_id for reference if needed, but registration_number is primary
    @Column(name = "admission_id")
    private Long admissionId;

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
        if (totalFees == null) totalFees = 0.0;
        if (feesDue == null) feesDue = 0.0;
        if (totalPaid == null) totalPaid = 0.0;
        if (feesRefund == null) feesRefund = 0.0;
        if (status == null) status = "Pending";
        if (isDeleted == null) isDeleted = false;
    }
}