package com.tts.sms.service;

import com.tts.sms.dto.PasswordResetDTO;
import com.tts.sms.exception.UnauthorizedException;
import com.tts.sms.model.User;
import com.tts.sms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private final UserRepository userRepository;
    private final EmailTemplateService emailTemplateService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * Get current logged-in user
     */
    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User) {
            return (User) authentication.getPrincipal();
        }
        throw new UnauthorizedException("No authenticated user found");
    }

    /**
     * Reset password for current logged-in user
     */
    @Transactional
    public void resetPassword(PasswordResetDTO resetDTO) {
        log.info("🔐 Password reset request initiated");

        // Get current user
        User currentUser = getCurrentUser();
        log.info("User: {} is attempting to reset password", currentUser.getUsername());

        // Validate current password
        if (!passwordEncoder.matches(resetDTO.getCurrentPassword(), currentUser.getPassword())) {
            log.warn(" Invalid current password for user: {}", currentUser.getUsername());
            throw new UnauthorizedException("Current password is incorrect");
        }

        // Validate new password matches confirm password
        if (!resetDTO.getNewPassword().equals(resetDTO.getConfirmPassword())) {
            log.warn(" New password and confirm password do not match");
            throw new IllegalArgumentException("New password and confirm password do not match");
        }

        // Validate new password is different from current
        if (passwordEncoder.matches(resetDTO.getNewPassword(), currentUser.getPassword())) {
            log.warn(" New password is same as current password");
            throw new IllegalArgumentException("New password must be different from current password");
        }

        // Validate password strength
        if (!validatePasswordStrength(resetDTO.getNewPassword())) {
            log.warn(" Password does not meet strength requirements");
            throw new IllegalArgumentException("Password does not meet security requirements");
        }

        // Update password
        currentUser.setPassword(passwordEncoder.encode(resetDTO.getNewPassword()));
        userRepository.save(currentUser);

        log.info(" Password reset successful for user: {}", currentUser.getUsername());

        // Send notification email
        try {
            emailTemplateService.sendPasswordChangedEmail(
                    currentUser.getEmployee().getEmailId(),
                    currentUser.getEmployee().getEmployeeName()
            );
            log.info("📧 Password change notification sent");
        } catch (Exception e) {
            log.warn(" Failed to send password change notification: {}", e.getMessage());
        }
    }

    /**
     * Validate password strength
     */
    private boolean validatePasswordStrength(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }

        boolean hasUpper = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLower = password.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasSpecial = password.chars().anyMatch(ch -> "!@#$%^&*(),.?\":{}|<>".indexOf(ch) >= 0);

        return hasUpper && hasLower && hasDigit && hasSpecial;
    }
}