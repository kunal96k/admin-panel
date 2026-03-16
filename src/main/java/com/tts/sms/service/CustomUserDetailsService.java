package com.tts.sms.service;

import java.time.LocalDateTime;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tts.sms.model.User;
import com.tts.sms.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.debug("Loading user by username: {}", username);

        User user = userRepository.findByUsernameOrEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        if (!user.getIsActive()) {
            throw new UsernameNotFoundException("User is inactive: " + username);
        }

        if (user.getIsLocked()) {
            throw new UsernameNotFoundException("User is locked: " + username);
        }

        // Update last login
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        log.debug("User loaded successfully: {} with role: {}", username, user.getRole().getRoleTitle());
        return user;
    }

    @Transactional
    public void incrementFailedAttempts(String username) {
        userRepository.findByUsernameOrEmail(username).ifPresent(user -> {
            int attempts = user.getFailedAttempts() + 1;
            user.setFailedAttempts(attempts);

            // Lock account after 5 failed attempts
            if (attempts >= 5) {
                user.setIsLocked(true);
                log.warn("User account locked due to too many failed attempts: {}", username);
            }

            userRepository.save(user);
        });
    }

    @Transactional
    public void resetFailedAttempts(String username) {
        userRepository.findByUsernameOrEmail(username).ifPresent(user -> {
            user.setFailedAttempts(0);
            userRepository.save(user);
        });
    }

    @Transactional
    public void incrementCaptchaAttempts(String username) {
        userRepository.findByUsernameOrEmail(username).ifPresent(user -> {
            int attempts = (user.getCaptchaAttempts() != null ? user.getCaptchaAttempts() : 0) + 1;
            user.setCaptchaAttempts(attempts);
            user.setLastCaptchaFail(LocalDateTime.now());

            // Lock CAPTCHA for 15 minutes after 3 failed attempts
            if (attempts >= 3) {
                user.setCaptchaLockedUntil(LocalDateTime.now().plusMinutes(1)); // DEV: 1 min | PROD: Change to .plusMinutes(15)
                log.warn("CAPTCHA locked for 1 minutes due to failed attempts: {}", username);
            }

            userRepository.save(user);
        });
    }

    @Transactional
    public void resetCaptchaAttempts(String username) {
        userRepository.findByUsernameOrEmail(username).ifPresent(user -> {
            user.setCaptchaAttempts(0);
            user.setCaptchaLockedUntil(null);
            user.setLastCaptchaFail(null);
            userRepository.save(user);
            log.info(" CAPTCHA attempts reset for user: {}", username);
        });
    }

    @Transactional
    public boolean isCaptchaLocked(String username) {
        User user = userRepository.findByUsernameOrEmail(username).orElse(null);
        if (user == null) {
            return false;
        }

        if (user.getCaptchaLockedUntil() == null) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        boolean isLocked = user.getCaptchaLockedUntil().isAfter(now);

        // Auto-unlock if time has passed - CRITICAL: Save in a new transaction
        if (!isLocked && user.getCaptchaAttempts() != null && user.getCaptchaAttempts() > 0) {
            user.setCaptchaAttempts(0);
            user.setCaptchaLockedUntil(null);
            user.setLastCaptchaFail(null);
            userRepository.saveAndFlush(user); // Use saveAndFlush to ensure immediate DB update
            log.info("🔓 CAPTCHA auto-unlocked for user: {}", username);
        }

        return isLocked;
    }
}