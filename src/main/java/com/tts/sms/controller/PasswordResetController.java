package com.tts.sms.controller;

import com.tts.sms.dto.PasswordResetDTO;
import com.tts.sms.service.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    /**
     * Reset password for current logged-in user
     */
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody PasswordResetDTO resetDTO,
            BindingResult bindingResult) {

        Map<String, String> response = new HashMap<>();

        // Check for validation errors
        if (bindingResult.hasErrors()) {
            String errors = bindingResult.getFieldErrors().stream()
                    .map(error -> error.getField() + ": " + error.getDefaultMessage())
                    .collect(Collectors.joining(", "));

            log.warn(" Validation errors: {}", errors);
            response.put("message", "Validation failed: " + errors);
            return ResponseEntity.badRequest().body(response);
        }

        try {
            passwordResetService.resetPassword(resetDTO);
            response.put("message", "Password reset successful. Please login with your new password.");
            return ResponseEntity.ok(response);

        } catch (com.tts.sms.exception.UnauthorizedException e) {
            // Handle incorrect current password
            log.warn(" Unauthorized: {}", e.getMessage());
            response.put("message", e.getMessage());
            response.put("field", "currentPassword");
            return ResponseEntity.status(401).body(response);

        } catch (IllegalArgumentException e) {
            // Handle validation errors (password mismatch, weak password, etc.)
            log.warn(" Validation failed: {}", e.getMessage());
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);

        } catch (Exception e) {
            log.error(" Password reset error: {}", e.getMessage(), e);
            response.put("message", "Failed to reset password. Please try again.");
            return ResponseEntity.internalServerError().body(response);
        }
    }
}