package com.tts.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * TechnoKraft Student Management System
 * Main Application Entry Point (Production Ready)
 *
 * Features:
 *  - Production-optimized Spring Boot configuration
 *  - Comprehensive startup logging with system information
 *  - Graceful shutdown handling
 *  - Environment-aware configuration
 *  - Security headers and error handling
 *
 * @author Sneha Ghatol, Kunal Patil
 * @version 1.0.2-prod
 * @since 2024
 */
@Slf4j
@EnableScheduling
@SpringBootApplication
public class SmsApplication {

    private static ConfigurableApplicationContext context;
    private static final String APP_VERSION = "1.0.2";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) {
        // Set system properties for production
        configureSystemProperties();

        // Start Spring Boot application
        SpringApplication app = new SpringApplication(SmsApplication.class);

        // Disable Spring Boot banner for cleaner logs (optional)
        // app.setBannerMode(Banner.Mode.OFF);

        context = app.run(args);
        Environment env = context.getEnvironment();

        // Log startup information
        logStartupInfo(env);
    }

    /**
     * Configure system properties for production deployment
     */
    private static void configureSystemProperties() {
        // Set Java system properties
        System.setProperty("java.awt.headless", "true");
        System.setProperty("file.encoding", "UTF-8");

        // Set timezone (adjust as needed)
        System.setProperty("user.timezone", "Asia/Kolkata");
    }

    /**
     * Log comprehensive startup information
     */
    private static void logStartupInfo(Environment env) {
        try {
            String appName = env.getProperty("spring.application.name", "TechnoKraft CRM");
            String port = env.getProperty("server.port", "8080");
            String contextPath = env.getProperty("server.servlet.context-path", "");
            String[] profiles = env.getActiveProfiles();
            String profilesStr = profiles.length > 0 ? String.join(", ", profiles) : "default";

            // Get system information
            String hostName = InetAddress.getLocalHost().getHostName();
            String hostAddress = InetAddress.getLocalHost().getHostAddress();
            String javaVersion = System.getProperty("java.version");
            String osName = System.getProperty("os.name");
            String osVersion = System.getProperty("os.version");
            String startTime = LocalDateTime.now().format(DATE_FORMATTER);

            // Memory information
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory() / (1024 * 1024);
            long totalMemory = runtime.totalMemory() / (1024 * 1024);
            long freeMemory = runtime.freeMemory() / (1024 * 1024);

            // Build startup banner
            StringBuilder banner = new StringBuilder();
            banner.append("\n")
                    .append("================================================================================\n")
                    .append("   ████████╗███████╗ ██████╗██╗  ██╗███╗   ██╗ ██████╗ ██╗  ██╗██████╗  █████╗ ███████╗████████╗\n")
                    .append("   ╚══██╔══╝██╔════╝██╔════╝██║  ██║████╗  ██║██╔═══██╗██║ ██╔╝██╔══██╗██╔══██╗██╔════╝╚══██╔══╝\n")
                    .append("      ██║   █████╗  ██║     ███████║██╔██╗ ██║██║   ██║█████╔╝ ██████╔╝███████║█████╗     ██║   \n")
                    .append("      ██║   ██╔══╝  ██║     ██╔══██║██║╚██╗██║██║   ██║██╔═██╗ ██╔══██╗██╔══██║██╔══╝     ██║   \n")
                    .append("      ██║   ███████╗╚██████╗██║  ██║██║ ╚████║╚██████╔╝██║  ██╗██║  ██║██║  ██║██║        ██║   \n")
                    .append("      ╚═╝   ╚══════╝ ╚═════╝╚═╝  ╚═╝╚═╝  ╚═══╝ ╚═════╝ ╚═╝  ╚═╝╚═╝  ╚═╝╚═╝  ╚═╝╚═╝        ╚═╝   \n")
                    .append("================================================================================\n")
                    .append("   Application    : ").append(appName).append("\n")
                    .append("   Version        : ").append(APP_VERSION).append("\n")
                    .append("   Started At     : ").append(startTime).append("\n")
                    .append("   Active Profile : ").append(profilesStr).append("\n")
                    .append("--------------------------------------------------------------------------------\n")
                    .append("   Access URLs:\n")
                    .append("   - Local        : http://localhost:").append(port).append(contextPath).append("\n")
                    .append("   - Network      : http://").append(hostAddress).append(":").append(port).append(contextPath).append("\n")
                    .append("   - Hostname     : http://").append(hostName).append(":").append(port).append(contextPath).append("\n")
                    .append("--------------------------------------------------------------------------------\n")
                    .append("   System Info:\n")
                    .append("   - Host         : ").append(hostName).append(" (").append(hostAddress).append(")\n")
                    .append("   - Java Version : ").append(javaVersion).append("\n")
                    .append("   - OS           : ").append(osName).append(" ").append(osVersion).append("\n")
                    .append("--------------------------------------------------------------------------------\n")
                    .append("   Memory Info:\n")
                    .append("   - Max Memory   : ").append(maxMemory).append(" MB\n")
                    .append("   - Total Memory : ").append(totalMemory).append(" MB\n")
                    .append("   - Free Memory  : ").append(freeMemory).append(" MB\n")
                    .append("--------------------------------------------------------------------------------\n")
                    .append("   Developers     : Sneha Ghatol | Kunal Patil\n")
                    .append("   Organization   : TechnoKraft Training & Solution Pvt. Ltd.\n")
                    .append("================================================================================\n");

            log.info(banner.toString());

            // Log important warnings for production
            if (profiles.length == 0 || "default".equals(profilesStr)) {
                log.warn("⚠️  WARNING: No active profile set! Consider using 'prod' profile for production.");
            }

        } catch (Exception e) {
            log.error("❌ Failed to display startup information", e);
            // Still log basic info
            log.info("\n------------------------------------------------------------\n" +
                            "   Application started but failed to gather full system info\n" +
                            "   Port: {}\n" +
                            "------------------------------------------------------------",
                    env.getProperty("server.port", "8080"));
        }
    }

    /**
     * Graceful shutdown handler
     */
    @EventListener(ContextClosedEvent.class)
    public void onShutdown() {
        log.info("\n================================================================================");
        log.info("   🛑 TechnoKraft CRM - Shutting down gracefully...");
        log.info("   Stopped at: {}", LocalDateTime.now().format(DATE_FORMATTER));
        log.info("================================================================================\n");
    }

    /**
     * Programmatic shutdown (if needed)
     */
    public static void shutdown() {
        if (context != null) {
            log.info("Initiating application shutdown...");
            SpringApplication.exit(context, () -> 0);
        }
    }
}