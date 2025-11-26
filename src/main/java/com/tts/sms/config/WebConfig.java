package com.tts.sms.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.server.MimeMappings;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.config.annotation.*;

import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
public class WebConfig implements WebMvcConfigurer {

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

        // JavaScript files - HIGHEST PRIORITY
        registry.addResourceHandler("/assets/js/**")
                .addResourceLocations("classpath:/static/assets/js/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic())
                .resourceChain(true);

        registry.addResourceHandler("/js/**")
                .addResourceLocations("classpath:/static/js/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic())
                .resourceChain(true);

        // CSS files
        registry.addResourceHandler("/assets/css/**")
                .addResourceLocations("classpath:/static/assets/css/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic())
                .resourceChain(true);

        registry.addResourceHandler("/css/**")
                .addResourceLocations("classpath:/static/css/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic())
                .resourceChain(true);

        // Images
        registry.addResourceHandler("/assets/images/**")
                .addResourceLocations("classpath:/static/assets/images/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic())
                .resourceChain(true);

        registry.addResourceHandler("/images/**")
                .addResourceLocations("classpath:/static/images/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic())
                .resourceChain(true);

        // Generic assets - LOWEST PRIORITY
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic())
                .resourceChain(true);

        // Upload directory - no cache
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:./uploads/")
                .setCacheControl(CacheControl.noCache())
                .resourceChain(false);

        log.info("✅ Resource handlers configured successfully");
    }

    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        log.debug("Configuring content negotiation");
        configurer
                .favorPathExtension(false)
                .favorParameter(false)
                .ignoreAcceptHeader(false)
                .defaultContentType(MediaType.TEXT_HTML)
                .mediaType("js", MediaType.valueOf("application/javascript"))
                .mediaType("mjs", MediaType.valueOf("application/javascript"))
                .mediaType("css", MediaType.valueOf("text/css"))
                .mediaType("json", MediaType.APPLICATION_JSON)
                .mediaType("xml", MediaType.APPLICATION_XML)
                .mediaType("map", MediaType.APPLICATION_JSON);

        log.info("✅ Content negotiation configured");
    }

    /**
     * Configure MIME type mappings at server level
     * This ensures correct MIME types are sent in HTTP headers
     */
    @Bean
    public WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> webServerFactoryCustomizer() {
        return factory -> {
            log.debug("Configuring custom MIME type mappings");

            MimeMappings mappings = new MimeMappings(MimeMappings.DEFAULT);

            // JavaScript MIME types - CRITICAL FIX
            mappings.add("js", "application/javascript");
            mappings.add("mjs", "application/javascript");

            // CSS MIME type
            mappings.add("css", "text/css");

            // JSON MIME types
            mappings.add("json", "application/json");
            mappings.add("map", "application/json");

            // Font MIME types
            mappings.add("woff", "font/woff");
            mappings.add("woff2", "font/woff2");
            mappings.add("ttf", "font/ttf");
            mappings.add("otf", "font/otf");
            mappings.add("eot", "application/vnd.ms-fontobject");

            // Image MIME types
            mappings.add("svg", "image/svg+xml");
            mappings.add("ico", "image/x-icon");
            mappings.add("png", "image/png");
            mappings.add("jpg", "image/jpeg");
            mappings.add("jpeg", "image/jpeg");
            mappings.add("gif", "image/gif");
            mappings.add("webp", "image/webp");

            factory.setMimeMappings(mappings);

            log.info("✅ Custom MIME type mappings configured successfully");
        };
    }
}
