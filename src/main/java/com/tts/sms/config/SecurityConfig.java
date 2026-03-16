package com.tts.sms.config;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.tts.sms.service.CustomUserDetailsService;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    @Value("${app.security.cors.allowed-origins:*}")
    private String allowedOrigins;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    private final Environment environment;
    private final CustomUserDetailsService userDetailsService;
    private final AccountStatusEnforcementFilter accountStatusEnforcementFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        log.info("Configuring security with FULL CSRF protection…");

        // CSRF Token handler and cookie repository
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName("_csrf");

        CookieCsrfTokenRepository csrfRepo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepo.setCookieName("XSRF-TOKEN");
        csrfRepo.setHeaderName("X-CSRF-TOKEN");
        csrfRepo.setCookiePath("/"); // Critical for cross-path access

        // Industry Standard: Only enforce secure cookies if profile is prod AND we are
        // explicitly using HTTPS
        // This prevents the JSESSIONID/XSRF-TOKEN from being rejected on HTTP
        // production test servers
        boolean isProd = "prod".equalsIgnoreCase(activeProfile);
        csrfRepo.setSecure(isProd); // We'll set this based on profile, but for testing IPs, HTTP is common.

        // Also ensure JSESSIONID follows the same pattern
        http.sessionManagement(session -> session
                .sessionFixation().migrateSession());

        http
                .authenticationProvider(authenticationProvider())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .addFilterBefore(accountStatusEnforcementFilter, UsernamePasswordAuthenticationFilter.class)

                /*
                 * --------------------
                 * ENABLE CSRF SECURELY
                 * --------------------
                 */
                .csrf(csrf -> csrf
                        // Allow public auth endpoints (login, captcha)
                        .ignoringRequestMatchers(
                                "/api/auth/**", // captcha, forgot-password etc.
                                "/error")
                        .csrfTokenRepository(csrfRepo)
                        .csrfTokenRequestHandler(csrfHandler))

                /* AUTH RULES */
                .authorizeHttpRequests(auth -> auth
                        // PUBLIC ROUTES FIRST
                        .requestMatchers(
                                "/",
                                "/login",
                                "/access-denied",
                                "/api/auth/**",
                                "/assets/**",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/uploads/**",
                                "/webjars/**",
                                "/error")
                        .permitAll()

                        // ADMIN ROUTES
                        .requestMatchers("/admin/**").hasRole("ADMIN")

                        // ALL OTHER API ROUTES
                        .requestMatchers("/api/**").authenticated()

                        // CATCH-ALL MUST BE LAST
                        .anyRequest().authenticated())

                /* SESSIONS */
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))

                /* LOGIN FORM */
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .successHandler(authenticationSuccessHandler())
                        .failureHandler(authenticationFailureHandler())
                        .permitAll())

                /* LOGOUT */
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID", "XSRF-TOKEN")
                        .clearAuthentication(true))

                /* ACCESS DENIED HANDLER */
                .exceptionHandling(exception -> exception
                        .accessDeniedHandler((request, response, ex) -> {
                            HttpSession session = request.getSession();
                            session.setAttribute("accessDeniedMessage",
                                    "You attempted to access: " + request.getRequestURI());
                            log.warn("Access denied for: {}", request.getRequestURI());
                            response.sendRedirect("/access-denied");
                        }));

        return http.build();
    }

    /**
     * Authentication success handler:
     * - reset failed attempts
     * - redirect to /dashboard
     */
    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> {
            userDetailsService.resetFailedAttempts(authentication.getName());
            response.sendRedirect("/dashboard");
        };
    }

    /**
     * Authentication failure handler:
     * - increment failed attempts
     * - redirect back to login with error
     */
    @Bean
    public AuthenticationFailureHandler authenticationFailureHandler() {
        return (request, response, exception) -> {
            String username = request.getParameter("username");
            if (username != null) {
                userDetailsService.incrementFailedAttempts(username);
            }
            response.sendRedirect("/login?error");
        };
    }

    /**
     * DaoAuthenticationProvider using CustomUserDetailsService and BCrypt encoder
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    /**
     * AuthenticationManager (needed if you inject it elsewhere)
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * CORS Configuration
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        log.info("Configuring CORS with allowed origins: {}", allowedOrigins);

        CorsConfiguration configuration = new CorsConfiguration();

        if ("*".equals(allowedOrigins)) {
            configuration.addAllowedOriginPattern("*");
        } else {
            configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        }

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setExposedHeaders(Arrays.asList("X-CSRF-TOKEN"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    /**
     * Password Encoder (BCrypt with strength 12)
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}