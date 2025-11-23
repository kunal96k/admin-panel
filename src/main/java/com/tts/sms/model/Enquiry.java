package com.tts.sms.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entity representing student enquiries
 * Supports both old format (single name field) and new format (first, middle, last name)
 */
@Entity
@Table(name = "enquiries", indexes = {
        @Index(name = "idx_mobile", columnList = "mobile"),
        @Index(name = "idx_email", columnList = "email"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_source", columnList = "source"),
        @Index(name = "idx_enquiry_date", columnList = "enquiry_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Enquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Student Name Fields (New Format)
    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "middle_name", length = 100)
    private String middleName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    // Old Format Support - for imported legacy data
    @Column(name = "full_name", length = 255)
    private String fullName;

    // Contact Information
    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Invalid mobile number format")
    @Column(name = "mobile", nullable = false, length = 15)
    private String mobile;

    @Column(name = "secondary_mobile", length = 15)
    @Pattern(regexp = "^$|^[6-9]\\d{9}$", message = "Invalid secondary mobile number format")
    private String secondaryMobile;

    @Email(message = "Invalid email format")
    @Column(name = "email", length = 100)
    private String email;

    @Email(message = "Invalid secondary email format")
    @Column(name = "secondary_email", length = 100)
    private String secondaryEmail;

    // Address Information
    @Column(name = "current_address", columnDefinition = "TEXT")
    private String currentAddress;

    @Column(name = "permanent_address", columnDefinition = "TEXT")
    private String permanentAddress;

    @Column(name = "pin_current", length = 10)
    private String pinCurrent;

    @Column(name = "pin_permanent", length = 10)
    private String pinPermanent;

    // Academic Information
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

    // Course Interest
    @NotBlank(message = "Course is required")
    @Column(name = "course", nullable = false, columnDefinition = "TEXT")
    private String course;

    @Column(name = "package_name", length = 150)
    private String packageName;

    @Column(name = "demo_lecture_required")
    private Boolean demoLectureRequired;

    @Column(name = "interest_level", length = 50)
    private String interestLevel;

    // Enquiry Details
    @NotBlank(message = "Enquiry source is required")
    @Column(name = "source", nullable = false, length = 100)
    private String source;

    @Column(name = "reference_name", length = 100)
    private String referenceName;

    @NotNull(message = "Enquiry date is required")
    @Column(name = "enquiry_date", nullable = false)
    private LocalDate enquiryDate;

    @Column(name = "followup_date")
    private LocalDate followupDate;

    @Column(name = "assign_to", length = 100)
    private String assignTo;

    @Column(name = "status", length = 50)
    @Builder.Default
    private String status = "New";

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

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

    @Column(name = "import_source", length = 50)
    private String importSource; // "OLD_CSV", "NEW_CSV", "MANUAL"

    // Helper method to get display name
    @Transient
    public String getDisplayName() {
        if (fullName != null && !fullName.isBlank()) {
            return fullName;
        }
        StringBuilder name = new StringBuilder();
        if (firstName != null) name.append(firstName).append(" ");
        if (middleName != null) name.append(middleName).append(" ");
        if (lastName != null) name.append(lastName);
        return name.toString().trim();
    }

    @PrePersist
    @PreUpdate
    private void validateData() {
        // Ensure either fullName or firstName exists
        if ((fullName == null || fullName.isBlank()) &&
                (firstName == null || firstName.isBlank())) {

            // Try to build full name from components if lastName exists
            if (lastName != null && !lastName.isBlank()) {
                StringBuilder name = new StringBuilder();
                if (middleName != null && !middleName.isBlank()) {
                    name.append(middleName).append(" ");
                }
                name.append(lastName);
                this.fullName = name.toString();
                return;
            }

            throw new IllegalStateException("Either full name or first name must be provided");
        }
    }
}