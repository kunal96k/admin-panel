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
        log.info("🔧 Setting cutoff date to: {} by {}", cutoffDate, updatedBy);

        SystemConfiguration config = configRepository.findByConfigKey(CUTOFF_DATE_KEY)
                .orElse(SystemConfiguration.builder()
                        .configKey(CUTOFF_DATE_KEY)
                        .description("Cutoff date to categorize students as Old/New/Pursuing")
                        .build());

        config.setConfigValue(cutoffDate.format(DATE_FORMATTER));
        config.setUpdatedBy(updatedBy);

        configRepository.save(config);
    }
}