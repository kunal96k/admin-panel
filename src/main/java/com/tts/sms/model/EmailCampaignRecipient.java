package com.tts.sms.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "email_campaign_recipients")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailCampaignRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private EmailCampaign campaign;

    @Column(name = "email", nullable = false, length = 200)
    private String email;

    /** PENDING | SENT | FAILED | SKIPPED */
    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "attempt_count")
    @Builder.Default
    private Integer attemptCount = 0;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
