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

@Slf4j
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.upload.courses.dir:./uploads/courses}")
    private String coursesUploadDir;

    @Value("${app.csv.upload-dir:./uploads/csv}")
    private String csvUploadDir;

    @PostConstruct
    public void initializeUploadDirectories() {
        try {
            Path coursesPath = Paths.get(coursesUploadDir);
            if (!Files.exists(coursesPath)) {
                Files.createDirectories(coursesPath);
                log.info("✅ Created courses upload directory: {}", coursesPath.toAbsolutePath());
            }

            Path csvPath = Paths.get(csvUploadDir);
            if (!Files.exists(csvPath)) {
                Files.createDirectories(csvPath);
                log.info("✅ Created CSV upload directory: {}", csvPath.toAbsolutePath());
            }
        } catch (IOException e) {
            log.error("❌ Failed to create upload directories", e);
            throw new RuntimeException("Could not create upload directories", e);
        }
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        log.debug("Configuring view controllers");
        registry.addViewController("/login").setViewName("login");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("*");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        log.debug("Configuring resource handlers with proper MIME types");

        registry.addResourceHandler("/assets/js/**")
                .addResourceLocations("classpath:/static/assets/js/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic())
                .resourceChain(true);

        registry.addResourceHandler("/.well-known/**")
                .addResourceLocations("classpath:/static/.well-known/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic())
                .resourceChain(true);

        registry.addResourceHandler("/js/**")
                .addResourceLocations("classpath:/static/js/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic())
                .resourceChain(true);

        registry.addResourceHandler("/assets/css/**")
                .addResourceLocations("classpath:/static/assets/css/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic())
                .resourceChain(true);

        registry.addResourceHandler("/css/**")
                .addResourceLocations("classpath:/static/css/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic())
                .resourceChain(true);

        registry.addResourceHandler("/assets/images/**")
                .addResourceLocations("classpath:/static/assets/images/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic())
                .resourceChain(true);

        registry.addResourceHandler("/images/**")
                .addResourceLocations("classpath:/static/images/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic())
                .resourceChain(true);

        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic())
                .resourceChain(true);

        registry.addResourceHandler("/uploads/courses/**")
                .addResourceLocations("file:" + coursesUploadDir + "/")
                .setCacheControl(CacheControl.noCache().mustRevalidate())
                .resourceChain(false);

        registry.addResourceHandler("/uploads/csv/**")
                .addResourceLocations("file:" + csvUploadDir + "/")
                .setCacheControl(CacheControl.noCache())
                .resourceChain(false);

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:./uploads/")
                .setCacheControl(CacheControl.noCache())
                .resourceChain(false);

        log.info("✅ Resource handlers configured successfully");
        log.info("📁 Course images directory: {}", Paths.get(coursesUploadDir).toAbsolutePath());
        log.info("📁 CSV upload directory: {}", Paths.get(csvUploadDir).toAbsolutePath());
    }

    // REMOVE THIS METHOD - Content negotiation now handled by ContentNegotiationConfig
    /*
    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        ...
    }
    */

    @Bean
    public WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> webServerFactoryCustomizer() {
        return factory -> {
            log.debug("Configuring custom MIME type mappings");

            MimeMappings mappings = new MimeMappings(MimeMappings.DEFAULT);

            mappings.add("js", "application/javascript");
            mappings.add("mjs", "application/javascript");
            mappings.add("css", "text/css");
            mappings.add("json", "application/json");
            mappings.add("map", "application/json");
            mappings.add("woff", "font/woff");
            mappings.add("woff2", "font/woff2");
            mappings.add("ttf", "font/ttf");
            mappings.add("otf", "font/otf");
            mappings.add("eot", "application/vnd.ms-fontobject");
            mappings.add("svg", "image/svg+xml");
            mappings.add("ico", "image/x-icon");
            mappings.add("png", "image/png");
            mappings.add("jpg", "image/jpeg");
            mappings.add("jpeg", "image/jpeg");
            mappings.add("gif", "image/gif");
            mappings.add("webp", "image/webp");
            mappings.add("bmp", "image/bmp");
            mappings.add("tiff", "image/tiff");
            mappings.add("csv", "text/csv");

            factory.setMimeMappings(mappings);

            log.info("✅ Custom MIME type mappings configured successfully");
        };
    }
}