package com.tts.sms.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Security headers configuration for HTTPS production deployment with Google Analytics
 * Adds essential security headers to all responses
 */
@Slf4j
@Configuration
public class SecurityHeadersConfig implements WebMvcConfigurer {

    @Value("${server.ssl.enabled:false}")
    private boolean sslEnabled;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(securityHeadersInterceptor());
        log.info(" Security headers interceptor registered (SSL: {})", sslEnabled);
    }

    @Bean
    public HandlerInterceptor securityHeadersInterceptor() {
        return new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request,
                                     HttpServletResponse response,
                                     Object handler) {

                // Skip security headers for static resources (performance optimization)
                String uri = request.getRequestURI();
                if (uri.startsWith("/assets/") ||
                        uri.startsWith("/uploads/") ||
                        uri.endsWith(".css") ||
                        uri.endsWith(".js") ||
                        uri.endsWith(".jpg") ||
                        uri.endsWith(".png") ||
                        uri.endsWith(".svg")) {
                    return true;
                }

                // ==================== HSTS ====================
                // HTTPS Strict Transport Security (production only)
                if (request.isSecure()) {
                    response.setHeader("Strict-Transport-Security",
                            "max-age=31536000; includeSubDomains; preload");
                }

                // ==================== CSP ====================
                // Content Security Policy
                // ==================== CSP ====================
                response.setHeader("Content-Security-Policy",
                        "default-src 'self' https:; " +

                                // Scripts: Include Google Analytics & Tag Manager
                                "script-src 'self' 'unsafe-inline' 'unsafe-eval' " +
                                "https://cdn.jsdelivr.net " +
                                "https://code.jquery.com " +
                                "https://cdnjs.cloudflare.com " +
                                "https://cdn.datatables.net " +
                                "https://www.googletagmanager.com " +
                                "https://www.google-analytics.com " +
                                "https://ssl.google-analytics.com; " +

                                // Styles
                                "style-src 'self' 'unsafe-inline' " +
                                "https://cdn.jsdelivr.net " +
                                "https://cdnjs.cloudflare.com " +
                                "https://fonts.googleapis.com " +
                                "https://cdn.datatables.net; " +

                                // Fonts
                                "font-src 'self' data: " +
                                "https://fonts.gstatic.com " +
                                "https://cdnjs.cloudflare.com " +
                                "https://cdn.jsdelivr.net; " +

                                // Images: Allow data URIs and HTTPS for GA
                                "img-src 'self' data: https: " +
                                "https://www.google-analytics.com " +
                                "https://ssl.google-analytics.com; " +

                                // Connect: Allow XHR/Fetch to GA AND CDNs (for .map files)
                                "connect-src 'self' " +
                                "https://www.google-analytics.com " +
                                "https://ssl.google-analytics.com " +
                                "https://www.googletagmanager.com " +
                                "https://stats.g.doubleclick.net " +
                                "https://cdn.jsdelivr.net " +
                                "https://cdnjs.cloudflare.com " +
                                "https://cdn.datatables.net; " +

                                // Other CSP directives
                                "frame-ancestors 'self'; " +
                                "frame-src 'self'; " +
                                "object-src 'none'; " +
                                "base-uri 'self'; " +
                                "form-action 'self'; "
                );

                // ==================== CLICKJACKING PROTECTION ====================
                response.setHeader("X-Frame-Options", "SAMEORIGIN");

                // ==================== MIME TYPE SNIFFING PROTECTION ====================
                response.setHeader("X-Content-Type-Options", "nosniff");

                // ==================== XSS PROTECTION (Legacy browsers) ====================
                response.setHeader("X-XSS-Protection", "1; mode=block");

                // ==================== REFERRER POLICY ====================
                response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

                // ==================== PERMISSIONS POLICY ====================
                response.setHeader("Permissions-Policy",
                        "geolocation=(), " +
                                "microphone=(), " +
                                "camera=(), " +
                                "payment=(), " +
                                "usb=(), " +
                                "magnetometer=(), " +
                                "gyroscope=(), " +
                                "accelerometer=(), " +
                                "interest-cohort=()");  // Disable FLoC

                // ==================== HIDE SERVER INFO ====================
                response.setHeader("Server", "TechnoKraft");
                response.setHeader("X-Powered-By", "");  // Remove X-Powered-By

                // ==================== CACHE CONTROL ====================
                // Prevent caching of sensitive pages
                if (uri.contains("/api/") || uri.contains("/admin/")) {
                    response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
                    response.setHeader("Pragma", "no-cache");
                    response.setHeader("Expires", "0");
                }

                return true;
            }
        };
    }
}