package com.tts.sms.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "email_templates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_name", nullable = false, length = 200)
    private String templateName;

    @Column(name = "email_subject", nullable = false, length = 300)
    private String emailSubject;

    /** ONLINE or OFFLINE */
    @Column(name = "conduct_mode", length = 20)
    @Builder.Default
    private String conductMode = "ONLINE";

    /** GOOGLE_MEET, ZOOM, OTHER */
    @Column(name = "online_platform", length = 50)
    @Builder.Default
    private String onlinePlatform = "GOOGLE_MEET";

    @Column(name = "meeting_link", length = 500)
    private String meetingLink;

    @Column(name = "venue_address", columnDefinition = "TEXT")
    private String venueAddress;

    /** Role / domain e.g. AI/ML Engineer */
    @Column(name = "hiring_role", length = 200)
    @Builder.Default
    private String hiringRole = "AI/ML Engineer";

    /** JOB, INTERNSHIP, or CAMPUS_DRIVE */
    @Column(name = "opportunity_type", length = 50)
    @Builder.Default
    private String opportunityType = "JOB";

    @Column(name = "event_date", length = 50)
    private String eventDate;

    @Column(name = "event_time", length = 50)
    private String eventTime;

    @Column(name = "custom_notes", columnDefinition = "TEXT")
    private String customNotes;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
