package com.tts.sms.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "email_campaigns")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campaign_name", nullable = false, length = 200)
    private String campaignName;

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

    /** IMMEDIATE or SCHEDULED */
    @Column(name = "send_mode", length = 20)
    @Builder.Default
    private String sendMode = "IMMEDIATE";

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    /** DRAFT | SENDING | SENT | SCHEDULED | FAILED */
    @Column(name = "status", length = 30)
    @Builder.Default
    private String status = "DRAFT";

    @Column(name = "total_recipients")
    @Builder.Default
    private Integer totalRecipients = 0;

    @Column(name = "sent_count")
    @Builder.Default
    private Integer sentCount = 0;

    @Column(name = "failed_count")
    @Builder.Default
    private Integer failedCount = 0;

    @Column(name = "skipped_count")
    @Builder.Default
    private Integer skippedCount = 0;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_by_name", length = 150)
    private String createdByName;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<EmailCampaignRecipient> recipients = new ArrayList<>();
}
