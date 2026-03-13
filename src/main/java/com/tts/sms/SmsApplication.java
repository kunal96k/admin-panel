package com.tts.sms;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.core.env.Environment;

import lombok.extern.slf4j.Slf4j;

/**
 * CRM TTS
 * Main Application Entry Point
 *
 * @author TechnoKraft Services LLP.
 * @version 4.0.0
 */
@Slf4j
@SpringBootApplication
public class SmsApplication extends SpringBootServletInitializer {

    /**
     * Required for deploying as WAR to external servlet container
     */
    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(SmsApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(SmsApplication.class);
        Environment env = app.run(args).getEnvironment();

        logApplicationStartup(env);
    }

    /**
     * Log application startup information
     */
    private static void logApplicationStartup(Environment env) {
        String protocol = env.getProperty("server.ssl.enabled", "false").equals("true")
                ? "https" : "http";
        String serverPort = env.getProperty("server.port");
        String contextPath = env.getProperty("server.servlet.context-path", "/");
        String hostAddress = "localhost";

        try {
            hostAddress = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            log.warn("Unable to determine host address", e);
        }

        log.info("\n----------------------------------------------------------\n" +
                        "Application '{}' is running!\n" +
                        "Profile(s): {}\n" +
                        "Access URLs:\n" +
                        "  Local:      {}://localhost:{}{}\n" +
                        "  External:   {}://{}:{}{}\n" +
                        "----------------------------------------------------------",
                env.getProperty("spring.application.name"),
                env.getActiveProfiles().length == 0 ? "default" : String.join(", ", env.getActiveProfiles()),
                protocol, serverPort, contextPath,
                protocol, hostAddress, serverPort, contextPath
        );
    }
}
