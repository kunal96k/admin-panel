package com.tts.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.net.InetAddress;

/**
 * TechnoKraft Student Management System
 * Main Application Entry Point (Development Mode)
 *
 * Simplified for local development:
 *  - Standard Spring Boot runnable JAR mode
 *  - Logs application URLs on startup
 *
 * @author TechnoKraft
 * @version 1.0.2-dev
 */
@Slf4j
@EnableScheduling
@SpringBootApplication
public class SmsApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(SmsApplication.class);
        Environment env = app.run(args).getEnvironment();

        try {
            String ip = InetAddress.getLocalHost().getHostAddress();
            String port = env.getProperty("server.port", "8080");
            log.info("\n------------------------------------------------------------\n" +
                            "   Application '{}' is running!\n" +
                            "   Local:      http://localhost:{}\n" +
                            "   External:   http://{}:{}\n" +
                            "   Active Profiles: {}\n" +
                            "------------------------------------------------------------",
                    env.getProperty("spring.application.name", "sms"),
                    port,
                    ip,
                    port,
                    String.join(", ", env.getActiveProfiles())
            );
        } catch (Exception e) {
            log.warn("Failed to determine system IP address", e);
        }
    }
}
