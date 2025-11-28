package com.tts.sms.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "employees")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_name", nullable = false, length = 50)
    private String employeeName;

    @Column(name = "mobile_number", nullable = false, length = 10)
    private String mobileNumber;

    @Column(name = "email_id", nullable = false, unique = true, length = 50)
    private String emailId;

    @Column(name = "designation", length = 20)
    private String designation;

    @Column(name = "gender", length = 1)
    private String gender;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "address", length = 100)
    private String address;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id")
    private Role role;

    @Column(name = "zoom_link", length = 200)
    private String zoomLink;

    @Column(name = "is_trainer")
    private Boolean isTrainer = false;

    @Column(name = "apply_batch_filter")
    private Boolean applyBatchFilter = false;

    @Column(name = "is_admin")
    private Boolean isAdmin = false;

    @Column(name = "username", length = 50)
    private String username;

    @Column(name = "password", length = 100)
    private String password;

    @Column(name = "photo", length = 255)
    private String photo;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_date")
    private LocalDateTime createdDate;

    @Column(name = "updated_date")
    private LocalDateTime updatedDate;

    // Mobile App Permissions
    @Column(name = "new_admission")
    private Boolean newAdmission = false;

    @Column(name = "view_admission")
    private Boolean viewAdmission = false;

    @Column(name = "acc_dashboard")
    private Boolean accDashboard = false;

    @Column(name = "counsellor_dash")
    private Boolean counsellorDash = false;

    @Column(name = "todays_followup")
    private Boolean todaysFollowup = false;

    @Column(name = "study_note")
    private Boolean studyNote = false;

    @Column(name = "fee_manager")
    private Boolean feeManager = false;

    @Column(name = "payment_link")
    private Boolean paymentLink = false;

    @Column(name = "time_table")
    private Boolean timeTable = false;

    @Column(name = "time_table_attendance")
    private Boolean timeTableAttendance = false;

    @Column(name = "overdue_followup")
    private Boolean overdueFollowup = false;

    @Column(name = "new_enquiry")
    private Boolean newEnquiry = false;

    @Column(name = "view_enquiry")
    private Boolean viewEnquiry = false;

    @Column(name = "batch_wise_fee")
    private Boolean batchWiseFee = false;

    @Column(name = "send_app_msg")
    private Boolean sendAppMsg = false;

    @Column(name = "manage_course")
    private Boolean manageCourse = false;

    @Column(name = "manage_batch")
    private Boolean manageBatch = false;

    @Column(name = "share_video")
    private Boolean shareVideo = false;

    @Column(name = "live_lecture")
    private Boolean liveLecture = false;

    @Column(name = "offline_exam")
    private Boolean offlineExam = false;

    @PrePersist
    protected void onCreate() {
        createdDate = LocalDateTime.now();
        updatedDate = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedDate = LocalDateTime.now();
    }
}