package com.tts.sms.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.server.MimeMappings;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * Production-ready web configuration with HTTPS support
 * Handles resource serving, CORS, MIME types, and caching
 */
@Slf4j
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.upload.courses.dir:./uploads/courses}")
    private String coursesUploadDir;

    @Value("${app.csv.upload-dir:./uploads/csv}")
    private String csvUploadDir;

    @Value("${server.ssl.enabled:false}")
    private boolean sslEnabled;

    @PostConstruct
    public void initializeUploadDirectories() {
        try {
            Path coursesPath = Paths.get(coursesUploadDir).toAbsolutePath();
            if (!Files.exists(coursesPath)) {
                Files.createDirectories(coursesPath);
                log.info(" Created courses upload directory: {}", coursesPath);
            }

            Path csvPath = Paths.get(csvUploadDir).toAbsolutePath();
            if (!Files.exists(csvPath)) {
                Files.createDirectories(csvPath);
                log.info(" Created CSV upload directory: {}", csvPath);
            }

            if (sslEnabled) {
                log.info("[SECURE] HTTPS/SSL is ENABLED - Production mode");
            } else {
                log.warn("[WARN] HTTPS/SSL is DISABLED - Development mode");
            }
        } catch (IOException e) {
            log.error("[FAIL] Failed to create upload directories", e);
            throw new RuntimeException("Could not create upload directories", e);
        }
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        log.debug("Configuring view controllers");
        registry.addViewController("/login").setViewName("login");
    }

    // CORS is now managed centrally in SecurityConfig.java for better reliability and security.
    // This prevents conflicting CORS headers and ensures consistent behavior.
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        log.debug("Global CORS is handled by SecurityConfig's CorsConfigurationSource");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        log.info("[RESOURCE] Configuring resource handlers for HTTPS production");

        // JavaScript resources with long cache
        registry.addResourceHandler("/assets/js/**")
                .addResourceLocations("classpath:/static/assets/js/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS)
                        .cachePublic()
                        .immutable())
                .resourceChain(true);

        registry.addResourceHandler("/js/**")
                .addResourceLocations("classpath:/static/js/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS)
                        .cachePublic()
                        .immutable())
                .resourceChain(true);

        // CSS resources with long cache
        registry.addResourceHandler("/assets/css/**")
                .addResourceLocations("classpath:/static/assets/css/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS)
                        .cachePublic()
                        .immutable())
                .resourceChain(true);

        registry.addResourceHandler("/css/**")
                .addResourceLocations("classpath:/static/css/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS)
                        .cachePublic()
                        .immutable())
                .resourceChain(true);

        // Image resources with long cache
        registry.addResourceHandler("/assets/images/**")
                .addResourceLocations("classpath:/static/assets/images/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS)
                        .cachePublic()
                        .immutable())
                .resourceChain(true);

        registry.addResourceHandler("/images/**")
                .addResourceLocations("classpath:/static/images/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS)
                        .cachePublic()
                        .immutable())
                .resourceChain(true);

        // General assets with long cache
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS)
                        .cachePublic()
                        .immutable())
                .resourceChain(true);

        // Well-known resources (SSL certificates, etc.)
        registry.addResourceHandler("/.well-known/**")
                .addResourceLocations("classpath:/static/.well-known/")
                .setCacheControl(CacheControl.maxAge(7, TimeUnit.DAYS)
                        .cachePublic())
                .resourceChain(true);

        // User uploaded content - NO CACHE
        registry.addResourceHandler("/uploads/courses/**")
                .addResourceLocations("file:" + Paths.get(coursesUploadDir).toAbsolutePath() + "/")
                .setCacheControl(CacheControl.noCache()
                        .mustRevalidate()
                        .cachePrivate())
                .resourceChain(false);

        registry.addResourceHandler("/uploads/csv/**")
                .addResourceLocations("file:" + Paths.get(csvUploadDir).toAbsolutePath() + "/")
                .setCacheControl(CacheControl.noCache()
                        .mustRevalidate()
                        .cachePrivate())
                .resourceChain(false);

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:./uploads/")
                .setCacheControl(CacheControl.noCache()
                        .mustRevalidate()
                        .cachePrivate())
                .resourceChain(false);


        log.info(" Resource handlers configured with Google Analytics support");

        log.info(" Resource handlers configured for production");
        log.info(" Course images: {}", Paths.get(coursesUploadDir).toAbsolutePath());
        log.info(" CSV uploads: {}", Paths.get(csvUploadDir).toAbsolutePath());
    }

    @Bean
    public WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> webServerFactoryCustomizer() {
        return factory -> {
            log.info("[CONFIG] Configuring MIME type mappings for production");

            MimeMappings mappings = new MimeMappings(MimeMappings.DEFAULT);

            // JavaScript
            mappings.add("js", "application/javascript; charset=UTF-8");
            mappings.add("mjs", "application/javascript; charset=UTF-8");
            mappings.add("map", "application/json; charset=UTF-8");

            // CSS
            mappings.add("css", "text/css; charset=UTF-8");

            // JSON
            mappings.add("json", "application/json; charset=UTF-8");

            // Fonts
            mappings.add("woff", "font/woff");
            mappings.add("woff2", "font/woff2");
            mappings.add("ttf", "font/ttf");
            mappings.add("otf", "font/otf");
            mappings.add("eot", "application/vnd.ms-fontobject");

            // Images
            mappings.add("svg", "image/svg+xml");
            mappings.add("ico", "image/x-icon");
            mappings.add("png", "image/png");
            mappings.add("jpg", "image/jpeg");
            mappings.add("jpeg", "image/jpeg");
            mappings.add("gif", "image/gif");
            mappings.add("webp", "image/webp");
            mappings.add("bmp", "image/bmp");
            mappings.add("tiff", "image/tiff");
            mappings.add("avif", "image/avif");

            // Documents
            mappings.add("pdf", "application/pdf");
            mappings.add("csv", "text/csv; charset=UTF-8");
            mappings.add("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            mappings.add("xls", "application/vnd.ms-excel");
            mappings.add("doc", "application/msword");
            mappings.add("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

            // Other
            mappings.add("xml", "application/xml; charset=UTF-8");
            mappings.add("txt", "text/plain; charset=UTF-8");
            mappings.add("html", "text/html; charset=UTF-8");

            factory.setMimeMappings(mappings);

            log.info(" MIME type mappings configured for production with UTF-8");
        };
    }
}