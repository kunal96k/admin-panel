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
@Table(
        name = "enquiries",
        indexes = {
                @Index(name = "idx_enquiry_no", columnList = "enquiry_no"),
                @Index(name = "idx_mobile", columnList = "mobile"),
                @Index(name = "idx_email", columnList = "email"),
                @Index(name = "idx_status", columnList = "status"),
                @Index(name = "idx_source", columnList = "source"),
                @Index(name = "idx_enquiry_date", columnList = "enquiry_date")
        }
)

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Enquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "enquiry_no", length = 50)
    private String enquiryNo;

    // Student Name Fields
    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "middle_name", length = 100)
    private String middleName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "full_name", length = 255)
    private String fullName;

    // Contact Information
    @Column(name = "mobile", length = 25)
    private String mobile;

    private String secondaryMobile;

    @Email(message = "Invalid email format")
    @Column(name = "email", length = 100)
    private String email;

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
    private String aadhaar;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "gender", length = 20)
    private String gender;

    // Course Interest - Use List<String> type
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "courses", columnDefinition = "json")
    @Builder.Default
    private List<String> courses = new ArrayList<>();

    @Column(name = "package_name", length = 150)
    private String packageName;

    @Column(name = "demo_lecture_required")
    private Boolean demoLectureRequired;

    @Column(name = "interest_level", length = 50)
    private String interestLevel;

    // Enquiry Details
    @NotBlank(message = "Enquiry source is required")
    @Column(name = "source", length = 100)
    private String source;

    @Column(name = "reference_name", length = 100)
    private String referenceName;

    @NotNull(message = "Enquiry date is required")
    @Column(name = "enquiry_date")
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
    @Column(name = "created_at", updatable = false)
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
    private String importSource;

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
        // Build full name if missing
        if (fullName == null || fullName.isBlank()) {
            StringBuilder name = new StringBuilder();
            if (firstName != null && !firstName.isBlank()) {
                name.append(firstName);
            }
            if (middleName != null && !middleName.isBlank()) {
                if (name.length() > 0) name.append(" ");
                name.append(middleName);
            }
            if (lastName != null && !lastName.isBlank()) {
                if (name.length() > 0) name.append(" ");
                name.append(lastName);
            }
            if (name.length() > 0) {
                this.fullName = name.toString();
            }
        }

        // Ensure courses list is not null and contains no empty values
        if (courses == null) {
            courses = new ArrayList<>();
        }
        courses.removeIf(c -> c == null || c.trim().isEmpty());

        // Ensure enquiry date is set
        if (enquiryDate == null) {
            enquiryDate = LocalDate.now();
        }

        // Ensure source is set
        if (source == null || source.isBlank()) {
            source = "Unknown";
        }

        // Ensure status is set
        if (status == null || status.isBlank()) {
            status = "New";
        }
    }
}