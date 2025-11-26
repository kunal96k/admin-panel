package com.tts.sms.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

@Entity
@Table(name = "enquiries")
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

    // Name fields
    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "middle_name", length = 100)
    private String middleName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "full_name", length = 255)
    private String fullName;

    // Contact
    @Column(name = "mobile", length = 50)
    private String mobile;

    @Column(name = "secondary_mobile", length = 50)
    private String secondaryMobile;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "secondary_email", length = 100)
    private String secondaryEmail;

    // Address
    @Column(name = "current_address", columnDefinition = "TEXT")
    private String currentAddress;

    @Column(name = "permanent_address", columnDefinition = "TEXT")
    private String permanentAddress;

    @Column(name = "pin_current", length = 10)
    private String pinCurrent;

    @Column(name = "pin_permanent", length = 10)
    private String pinPermanent;

    // Academic
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

    // Course
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
    @Column(name = "source", length = 100)
    private String source;

    @Column(name = "reference_name", length = 100)
    private String referenceName;

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
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

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

    /**
     * MINIMAL PrePersist - Only set defaults for NULL fields
     * NO validation, NO data modification
     */
    @PrePersist
    @PreUpdate
    private void setDefaults() {
        // Set timestamps
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        updatedAt = LocalDateTime.now();

        // Set defaults only if NULL
        if (enquiryDate == null) {
            enquiryDate = LocalDate.now();
        }

        if (source == null) {
            source = "N/A";
        }

        if (status == null) {
            status = "New";
        }

        if (courses == null || courses.isEmpty()) {
            courses = new ArrayList<>(List.of("N/A"));
        }

        // Build full name only if ALL name fields are present
        if (fullName == null && firstName != null && lastName != null) {
            StringBuilder name = new StringBuilder();
            name.append(firstName);
            if (middleName != null && !middleName.equals("N/A")) {
                name.append(" ").append(middleName);
            }
            name.append(" ").append(lastName);
            fullName = name.toString();
        }
    }
}