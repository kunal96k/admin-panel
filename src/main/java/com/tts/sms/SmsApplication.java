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
     * Log application startup information including CORS and Security settings
     */
    private static void logApplicationStartup(Environment env) {
        String protocol = "http";
        if (env.getProperty("server.ssl.key-store") != null || 
            "true".equals(env.getProperty("server.ssl.enabled")) ||
            "true".equals(env.getProperty("spring.security.require-ssl"))) {
            protocol = "https";
        }
        
        String serverPort = env.getProperty("server.port", "8080");
        String contextPath = env.getProperty("server.servlet.context-path", "");
        if ("/".equals(contextPath)) contextPath = "";
        
        String hostAddress = "localhost";
        try {
            hostAddress = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            log.warn("Unable to determine host address, using 'localhost'");
        }

        String allowedOrigins = env.getProperty("app.security.cors.allowed-origins", "*");
        String activeProfiles = env.getActiveProfiles().length == 0 ? "default" : String.join(", ", env.getActiveProfiles());

        log.info("\n" +
                        "==========================================================================\n" +
                        "                [READY] TTS SMS APPLICATION STARTED SUCCESSFULLY!         \n" +
                        "==========================================================================\n" +
                        "  Application   : {}\n" +
                        "  Profiles      : {}\n" +
                        "  Port          : {}\n" +
                        "  SSL Enabled   : {}\n" +
                        "  Context Path  : {}\n" +
                        "  CORS Allowed  : {}\n" +
                        "  Access URLs   :\n" +
                        "    Local       : {}://localhost:{}{}\n" +
                        "    External    : {}://{}:{}{}\n" +
                        "==========================================================================",
                env.getProperty("spring.application.name", "TTS-SMS"),
                activeProfiles,
                serverPort,
                protocol.equals("https") ? "YES" : "NO (HTTP Mode)",
                contextPath.isEmpty() ? "/" : contextPath,
                allowedOrigins,
                protocol, serverPort, contextPath,
                protocol, hostAddress, serverPort, contextPath
        );
    }
}
