package com.tts.sms.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Configuration to handle Google Analytics requests gracefully
 */
@Slf4j
@Configuration
public class AnalyticsConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new GoogleAnalyticsInterceptor())
                .addPathPatterns("/gtag/**", "/google-analytics/**", "/**/gtag/**");
    }

    /**
     * Interceptor to silently handle Google Analytics script requests
     */
    private static class GoogleAnalyticsInterceptor implements HandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            String uri = request.getRequestURI();

            if (uri.contains("gtag") || uri.contains("google-analytics") || uri.contains("googletagmanager")) {
                log.debug("Intercepted analytics request: {}", uri);
                response.setStatus(HttpServletResponse.SC_OK);
                return false; // Don't continue processing
            }

            return true;
        }
    }
}