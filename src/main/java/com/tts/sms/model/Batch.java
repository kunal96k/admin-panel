package com.tts.sms.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;

@Entity
@Table(name = "batches")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Batch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_no", nullable = false, unique = true, length = 20)
    private String batchNo;

    @Column(name = "batch_name", nullable = false, length = 100)
    private String batchName;

    @Column(name = "batch_size", nullable = false)
    private Integer batchSize;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    // Weekly schedule
    @Column(name = "is_sunday")
    private Boolean isSunday = false;

    @Column(name = "is_monday")
    private Boolean isMonday = false;

    @Column(name = "is_tuesday")
    private Boolean isTuesday = false;

    @Column(name = "is_wednesday")
    private Boolean isWednesday = false;

    @Column(name = "is_thursday")
    private Boolean isThursday = false;

    @Column(name = "is_friday")
    private Boolean isFriday = false;

    @Column(name = "is_saturday")
    private Boolean isSaturday = false;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "course_id")
    private Course course;

    @Column(name = "status", length = 20)
    private String status = "Active";

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @Column(name = "created_date")
    private LocalDateTime createdDate;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    @Column(name = "updated_date")
    private LocalDateTime updatedDate;

    @PrePersist
    protected void onCreate() {
        createdDate = LocalDateTime.now();
        updatedDate = LocalDateTime.now();
        if (status == null) {
            status = "Active";
        }
        if (isActive == null) {
            isActive = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedDate = LocalDateTime.now();
    }
}