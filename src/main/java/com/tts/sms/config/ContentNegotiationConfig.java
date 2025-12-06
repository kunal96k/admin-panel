package com.tts.sms.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Production-ready content negotiation configuration for HTTPS deployment
 * Handles proper content types for API endpoints and web resources
 */
@Slf4j
@Configuration
public class ContentNegotiationConfig implements WebMvcConfigurer {

    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        log.info("🔧 Configuring Content Negotiation for HTTPS Production");

        configurer
                // CRITICAL: Disable path extension to prevent .json/.xml suffix issues
                .favorPathExtension(false)

                // Don't use query parameters for content negotiation
                .favorParameter(false)

                // IMPORTANT: Always respect Accept header from client
                .ignoreAcceptHeader(false)

                // Allow unregistered media types for flexibility
                .useRegisteredExtensionsOnly(false)

                // Default to JSON for API endpoints (critical for 406 prevention)
                .defaultContentType(MediaType.APPLICATION_JSON)

                // Register common media types
                .mediaType("json", MediaType.APPLICATION_JSON)
                .mediaType("xml", MediaType.APPLICATION_XML)
                .mediaType("html", MediaType.TEXT_HTML)
                .mediaType("js", MediaType.valueOf("application/javascript"))
                .mediaType("mjs", MediaType.valueOf("application/javascript"))
                .mediaType("css", MediaType.valueOf("text/css"))
                .mediaType("csv", MediaType.valueOf("text/csv"))
                .mediaType("pdf", MediaType.APPLICATION_PDF)

                // Image types
                .mediaType("png", MediaType.IMAGE_PNG)
                .mediaType("jpg", MediaType.IMAGE_JPEG)
                .mediaType("jpeg", MediaType.IMAGE_JPEG)
                .mediaType("gif", MediaType.IMAGE_GIF)
                .mediaType("webp", MediaType.valueOf("image/webp"))
                .mediaType("svg", MediaType.valueOf("image/svg+xml"))

                // Font types
                .mediaType("woff", MediaType.valueOf("font/woff"))
                .mediaType("woff2", MediaType.valueOf("font/woff2"))
                .mediaType("ttf", MediaType.valueOf("font/ttf"))
                .mediaType("eot", MediaType.valueOf("application/vnd.ms-fontobject"));

        log.info("✅ Content Negotiation configured for HTTPS production");
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        // CRITICAL: Disable suffix pattern matching
        // Prevents /api/data.json being treated differently than /api/data
        configurer.setUseSuffixPatternMatch(false);

        // Allow both /api/endpoint and /api/endpoint/ to work
        configurer.setUseTrailingSlashMatch(true);

        log.info("✅ Path matching configured for production");
    }
}