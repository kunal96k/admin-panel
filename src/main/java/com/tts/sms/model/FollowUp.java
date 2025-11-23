package com.tts.sms.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "followups")
public class FollowUp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "enquiry_id")
    private Enquiry enquiry;

    private LocalDate followUpDate;
    private LocalDate nextFollowUpDate;
    private String mode;
    private String note;
    private LocalDateTime createdAt;
}