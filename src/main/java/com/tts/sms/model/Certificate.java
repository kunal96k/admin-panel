package com.tts.sms.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "certificates")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Certificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "registration_no", nullable = false)
    private String registrationNo;

    @Column(name = "certificate_no", unique = true)
    private String certificateNo;

    @Column(name = "student_name", nullable = false)
    private String studentName;

    @Column(name = "student_email")
    private String studentEmail;

    @Column(name = "course_name", nullable = false)
    private String courseName;

    @Column(name = "batch")
    private String batch;

    @Column(name = "grade")
    private String grade;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "course_from_date")
    private LocalDate courseFromDate;

    @Column(name = "course_to_date")
    private LocalDate courseToDate;

    @Column(name = "status", nullable = false)
    private String status = "Not Issued"; // "Not Issued " or "Issued"

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", referencedColumnName = "id")
    private Course course;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}