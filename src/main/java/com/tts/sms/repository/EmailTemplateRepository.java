package com.tts.sms.repository;

import com.tts.sms.model.EmailTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, Long> {
    List<EmailTemplate> findByIsActiveTrueOrderByCreatedAtDesc();
    boolean existsByTemplateNameIgnoreCase(String templateName);
}
