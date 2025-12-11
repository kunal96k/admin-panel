package com.tts.sms.controller;

import com.tts.sms.dto.CaptchaVerificationRequest;
import com.tts.sms.dto.ForgotPasswordRequest;
import com.tts.sms.model.User;
import com.tts.sms.service.CaptchaService;
import com.tts.sms.service.CustomUserDetailsService;
import com.tts.sms.service.ForgotPasswordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final ForgotPasswordService forgotPasswordService;
    private final CaptchaService captchaService;
    private final CustomUserDetailsService userDetailsService;

    /**
     * Generate new CAPTCHA
     */
    @GetMapping("/captcha")
    public ResponseEntity<Map<String, Object>> generateCaptcha() {
        Map<String, Object> captcha = captchaService.generateCaptcha();
        return ResponseEntity.ok(captcha);
    }

    /**
     *  Get current logged-in user details
     */
    @GetMapping("/current-user")
    public ResponseEntity<Map<String, Object>> getCurrentUser(Authentication authentication) {
        log.info("GET /api/auth/current-user");

        Map<String, Object> response = new HashMap<>();

        if (authentication == null || !authentication.isAuthenticated()) {
            response.put("authenticated", false);
            return ResponseEntity.ok(response);
        }

        try {
            User user = (User) authentication.getPrincipal();

            response.put("authenticated", true);
            response.put("userId", user.getId());
            response.put("username", user.getUsername());

            if (user.getEmployee() != null) {
                response.put("employeeId", user.getEmployee().getId());
                response.put("employeeName", user.getEmployee().getEmployeeName());
                response.put("email", user.getEmployee().getEmailId());

                if (user.getEmployee().getRole() != null) {
                    response.put("roleTitle", user.getEmployee().getRole().getRoleTitle());
                }
            }

            log.info(" Current user: {}", response.get("employeeName"));
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Error fetching current user", e);
            response.put("authenticated", false);
            response.put("error", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Verify CAPTCHA before login
     */
    // Replace existing verifyCaptcha method (lines 33-52) with:
    @PostMapping("/verify-captcha")
    public ResponseEntity<Map<String, Object>> verifyCaptcha(@Valid @RequestBody CaptchaVerificationRequest request) {
        Map<String, Object> response = new HashMap<>();

        try {
            // Check if CAPTCHA is locked
            if (userDetailsService.isCaptchaLocked(request.getUsername())) {
                response.put("success", false);
                response.put("message", "Too many failed attempts. Please try again after 15 minutes.");
                return ResponseEntity.status(429).body(response);
            }

            // Verify CAPTCHA
            boolean isValid = captchaService.verifyCaptcha(request.getCaptchaToken(), request.getCaptchaAnswer());

            if (isValid) {
                userDetailsService.resetCaptchaAttempts(request.getUsername());
                response.put("success", true);
                response.put("message", " CAPTCHA verified successfully");
                return ResponseEntity.ok(response);
            } else {
                userDetailsService.incrementCaptchaAttempts(request.getUsername());
                response.put("success", false);
                response.put("message", " Incorrect CAPTCHA. Please try again.");

                // Generate new captcha
                Map<String, Object> newCaptcha = captchaService.generateCaptcha();
                response.put("newCaptcha", newCaptcha);

                return ResponseEntity.status(400).body(response);
            }

        } catch (Exception e) {
            log.error(" Error verifying CAPTCHA: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "Error verifying CAPTCHA");
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Forgot password endpoint
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        Map<String, String> response = new HashMap<>();

        try {
            boolean success = forgotPasswordService.processForgotPassword(request.getEmail());

            if (success) {
                response.put("success", "true");
                response.put("message", " Password has been sent to your registered email address.");
                return ResponseEntity.ok(response);
            } else {
                response.put("success", "false");
                response.put("message", " Email address not found or not authorized. Please contact administrator.");
                return ResponseEntity.status(400).body(response);
            }

        } catch (Exception e) {
            log.error(" Error processing forgot password: {}", e.getMessage());
            response.put("success", "false");
            response.put("message", "Failed to process request. Please try again later.");
            return ResponseEntity.status(500).body(response);
        }
    }
}