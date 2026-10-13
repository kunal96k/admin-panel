package com.tts.sms.scheduler;

import com.tts.sms.service.EmailCampaignService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailCampaignScheduler {

    private final EmailCampaignService emailCampaignService;

    /**
     * Runs every minute to check for scheduled campaigns that are due to be sent.
     */
    @Scheduled(cron = "0 * * * * *")
    public void processScheduledCampaigns() {
        try {
            emailCampaignService.processScheduledCampaigns();
        } catch (Exception e) {
            log.error("[CAMPAIGN-SCHEDULER] Error processing scheduled campaigns: {}", e.getMessage(), e);
        }
    }
}
