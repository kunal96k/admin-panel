package com.tts.sms.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuration to enable Spring's scheduled task execution
 */
@Configuration
@EnableScheduling
public class SchedulerConfig {
    // Spring Boot will automatically detect @Scheduled methods
}