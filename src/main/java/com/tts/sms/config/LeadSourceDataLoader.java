package com.tts.sms.config;

import com.tts.sms.model.LeadSource;
import com.tts.sms.repository.LeadSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class LeadSourceDataLoader implements CommandLineRunner {

    private final LeadSourceRepository leadSourceRepository;

    @Override
    public void run(String... args) {
        if (leadSourceRepository.count() == 0) {
            log.info("Loading default lead source data...");

            List<String> defaultLeadSources = Arrays.asList(
                    "Walk-in",
                    "Website",
                    "Social Media",
                    "Referral",
                    "Newspaper Advertisement",
                    "Google Ads"
            );

            for (String sourceTitle : defaultLeadSources) {
                LeadSource leadSource = new LeadSource();
                leadSource.setSourceTitle(sourceTitle);
                leadSource.setIsActive(true);
                leadSourceRepository.save(leadSource);
            }

            log.info("Default lead source data loaded successfully. Total lead sources: {}", defaultLeadSources.size());
        } else {
            log.info("Lead source data already exists. Skipping default data load.");
        }
    }
}