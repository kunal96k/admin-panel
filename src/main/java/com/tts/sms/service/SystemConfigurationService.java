package com.tts.sms.service;

import com.tts.sms.model.SystemConfiguration;
import com.tts.sms.repository.SystemConfigurationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigurationService {

    private final SystemConfigurationRepository configRepository;
    private static final String CUTOFF_DATE_KEY = "STUDENT_CATEGORY_CUTOFF_DATE";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    @Transactional(readOnly = true)
    public LocalDate getCutoffDate() {
        return configRepository.findByConfigKey(CUTOFF_DATE_KEY)
                .map(config -> LocalDate.parse(config.getConfigValue(), DATE_FORMATTER))
                .orElse(LocalDate.of(2025, 8, 1)); // Default: August 1, 2025
    }

    @Transactional
    public void setCutoffDate(LocalDate cutoffDate, String updatedBy) {
        log.info("[CONFIG] Setting cutoff date to: {} by {}", cutoffDate, updatedBy);

        SystemConfiguration config = configRepository.findByConfigKey(CUTOFF_DATE_KEY)
                .orElse(SystemConfiguration.builder()
                        .configKey(CUTOFF_DATE_KEY)
                        .description("Cutoff date to categorize students as Old/New/Pursuing")
                        .build());

        config.setConfigValue(cutoffDate.format(DATE_FORMATTER));
        config.setUpdatedBy(updatedBy);

        configRepository.save(config);
    }

    private static final String FEE_REMINDER_ENABLED_KEY = "FEE_REMINDER_EMAIL_ENABLED";

    @Transactional(readOnly = true)
    public boolean isFeeReminderEmailEnabled() {
        return configRepository.findByConfigKey(FEE_REMINDER_ENABLED_KEY)
                .map(config -> Boolean.parseBoolean(config.getConfigValue()))
                .orElse(true); // Default: true (enabled)
    }

    @Transactional
    public void setFeeReminderEmailEnabled(boolean enabled, String updatedBy) {
        log.info("[CONFIG] Setting fee reminder email enabled to: {} by {}", enabled, updatedBy);

        SystemConfiguration config = configRepository.findByConfigKey(FEE_REMINDER_ENABLED_KEY)
                .orElse(SystemConfiguration.builder()
                        .configKey(FEE_REMINDER_ENABLED_KEY)
                        .description("Master toggle to enable or disable daily automated fee due reminder emails")
                        .build());

        config.setConfigValue(String.valueOf(enabled));
        config.setUpdatedBy(updatedBy);

        configRepository.save(config);
    }
}