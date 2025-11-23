
package com.tts.sms.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.TimeZone;

@Slf4j
@Configuration
@EnableAsync
@EnableScheduling
@EnableTransactionManagement
public class ApplicationConfig {

    @Value("${app.timezone:Asia/Kolkata}")
    private String applicationTimezone;

    /**
     * Initialize application timezone to IST (Indian Standard Time)
     */
    @PostConstruct
    public void initializeTimezone() {
        TimeZone.setDefault(TimeZone.getTimeZone(applicationTimezone));
        log.info("Application timezone set to: {} ({})",
                applicationTimezone,
                TimeZone.getDefault().getDisplayName());
        log.info("Current time in IST: {}", java.time.ZonedDateTime.now());
    }

    /**
     * Initialize application directories
     */
    @Bean
    public boolean initializeDirectories() {
        try {
            Path uploadsDir = Paths.get("./uploads/csv");
            if (!Files.exists(uploadsDir)) {
                Files.createDirectories(uploadsDir);
                log.info("Created uploads directory: {}", uploadsDir.toAbsolutePath());
            }

            Path logsDir = Paths.get("./logs");
            if (!Files.exists(logsDir)) {
                Files.createDirectories(logsDir);
                log.info("Created logs directory: {}", logsDir.toAbsolutePath());
            }

            return true;
        } catch (Exception e) {
            log.error("Failed to create directories", e);
            return false;
        }
    }

    /**
     * Database initialization info
     */
    @PostConstruct
    public void logDatabaseInfo() {
        log.info("===========================================");
        log.info("Database Configuration:");
        log.info("- Auto-create enabled: createDatabaseIfNotExist=true");
        log.info("- Timezone: Asia/Kolkata (IST)");
        log.info("- DDL Auto: update (will create tables automatically)");
        log.info("===========================================");
    }
}