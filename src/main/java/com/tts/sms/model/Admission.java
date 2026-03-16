package com.tts.sms.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

@Entity
@Table(name = "admissions", indexes = {
        @Index(name = "idx_reg_no", columnList = "registration_number"),
        @Index(name = "idx_mobile", columnList = "mobile_primary"),
        @Index(name = "idx_enquiry_id", columnList = "enquiry_id"),
        @Index(name = "idx_admission_date", columnList = "admission_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Admission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "registration_number", unique = true, length = 50)
    private String registrationNumber;

    @Column(name = "roll_number", length = 50)
    private String rollNumber;

    @Column(name = "admission_date", nullable = false)
    private LocalDate admissionDate;

    @Column(name = "enquiry_id")
    private Long enquiryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enquiry_id", insertable = false, updatable = false)
    private Enquiry enquiry;

    // Personal Information
    @NotBlank(message = "First name is required")
    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "middle_name", length = 100)
    private String middleName;

    @NotBlank(message = "Last name is required")
    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "college", length = 200)
    private String college;

    @Column(name = "qualification", length = 100)
    private String qualification;

    @Column(name = "aadhaar", length = 12)
    @Pattern(regexp = "^$|^\\d{12}$", message = "Aadhaar must be 12 digits")
    private String aadhaar;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "gender", length = 20)
    private String gender;

    @Column(name = "cast", length = 50)
    private String cast;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "physically_handicapped", length = 10)
    private String physicallyHandicapped;

    @Column(name = "blood_group", length = 10)
    private String bloodGroup;

    // Contact Information
    @Column(name = "mobile_primary", length = 15)
    private String mobilePrimary;

    @Column(name = "mobile_secondary", length = 15)
    private String mobileSecondary;

    @Email(message = "Invalid email format")
    @Column(name = "email_primary", length = 100)
    private String emailPrimary;

    @Email(message = "Invalid secondary email format")
    @Column(name = "email_secondary", length = 100)
    private String emailSecondary;

    @Column(name = "current_address", columnDefinition = "TEXT")
    private String currentAddress;

    @Column(name = "permanent_address", columnDefinition = "TEXT")
    private String permanentAddress;

    @Column(name = "pin_code_current", length = 10)
    private String pinCodeCurrent;

    @Column(name = "pin_code_permanent", length = 10)
    private String pinCodePermanent;

    @Column(name = "package_name", length = 150)
    private String packageName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "courses", columnDefinition = "json")
    @Builder.Default
    private List<String> courses = new ArrayList<>();

    @Column(name = "total_payable_fees")
    private Double totalPayableFees;

    @Column(name = "total_receivable_fees")
    private Double totalReceivableFees;

    @Column(name = "discount_percent")
    private Double discountPercent;

    @Column(name = "discount_amount")
    private Double discountAmount;

    // Batch Details - **CRITICAL FIX: Use List<String> type**
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "batches", columnDefinition = "json")
    @Builder.Default
    private List<String> batches = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "subjects", columnDefinition = "json")
    @Builder.Default
    private List<String> subjects = new ArrayList<>();

    @Column(name = "academic_year", length = 20)
    private String academicYear;

    // Other Details
    @Column(name = "document_type", length = 100)
    private String documentType;

    @Column(name = "lead_source", length = 100)
    private String leadSource;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "photo_path", length = 255)
    private String photoPath;

    @Column(name = "status", length = 50)
    @Builder.Default
    private String status = "Active";

    // Metadata
    @CreationTimestamp
    @Column(name = "created_at",nullable = false, updatable = false)
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

    /**
     * Import source to distinguish new admissions from CSV imports
     * Values: "NEW_ENTRY", "IMPORTED_OLD_DATA"
     */
    @Column(name = "import_source", length = 50)
    @Builder.Default
    private String importSource = "NEW_ENTRY";

    /**
     * Student category badge: OLD_STUDENT, NEW_STUDENT, PURSUING, COMPLETED, CANCELLED
     */
    @Column(name = "student_category", length = 50)
    @Builder.Default
    private String studentCategory = "PURSUING";

    /**
     * Last category update timestamp
     */
    @Column(name = "category_updated_at")
    private LocalDateTime categoryUpdatedAt;

    @Transient
    public String getFullName() {
        StringBuilder name = new StringBuilder();
        if (firstName != null && !firstName.isEmpty()) name.append(firstName).append(" ");
        if (middleName != null && !middleName.isEmpty()) name.append(middleName).append(" ");
        if (lastName != null && !lastName.isEmpty()) name.append(lastName);
        return name.toString().trim();
    }

    @Transient
    public void setFullName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            this.firstName = "";
            this.middleName = "";
            this.lastName = "";
            return;
        }
        
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) {
            this.firstName = parts[0];
            this.middleName = "";
            this.lastName = "";
        } else if (parts.length == 2) {
            this.firstName = parts[0];
            this.middleName = "";
            this.lastName = parts[1];
        } else {
            this.firstName = parts[0];
            this.lastName = parts[parts.length - 1];
            StringBuilder middle = new StringBuilder();
            for (int i = 1; i < parts.length - 1; i++) {
                middle.append(parts[i]).append(" ");
            }
            this.middleName = middle.toString().trim();
        }
    }


    @PrePersist
    private void prePersist() {
        if (admissionDate == null) {
            admissionDate = LocalDate.now();
        }
        if (status == null) {
            status = "Active";
        }
        if (courses == null) {
            courses = new ArrayList<>();
        }
        if (batches == null) {
            batches = new ArrayList<>();
        }
        if (subjects == null) {
            subjects = new ArrayList<>();
        }
    }
}