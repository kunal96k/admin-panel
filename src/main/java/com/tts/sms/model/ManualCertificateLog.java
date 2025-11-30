package com.tts.sms.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Tracks manual certificate generation for audit purposes
 */
@Entity
@Table(name = "manual_certificate_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManualCertificateLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "registration_no", nullable = false)
    private String registrationNo;

    @Column(name = "student_name", nullable = false)
    private String studentName;

    @Column(name = "course_name", nullable = false)
    private String courseName;

    @Column(name = "created_by_employee_id", nullable = false)
    private Long createdByEmployeeId;

    @Column(name = "created_by_employee_name", nullable = false)
    private String createdByEmployeeName;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "certificate_id")
    private Long certificateId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

}