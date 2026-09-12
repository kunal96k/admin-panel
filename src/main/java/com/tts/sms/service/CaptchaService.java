package com.tts.sms.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class CaptchaService {

    private final SecureRandom random = new SecureRandom();
    private final Map<String, CaptchaData> captchaStore = new ConcurrentHashMap<>();

    private static final String CAPTCHA_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final int CAPTCHA_LENGTH = 6;
    private static final int CAPTCHA_EXPIRY_MINUTES = 1;

    /**
     * Generate a new alphanumeric CAPTCHA challenge
     * @return Map containing captchaText and token
     */
    public Map<String, Object> generateCaptcha() {
        String captchaText = generateAlphanumericCaptcha();
        String token = generateSecureToken();

        // Store CAPTCHA with expiration time
        CaptchaData captchaData = new CaptchaData(
                captchaText,
                LocalDateTime.now().plusMinutes(CAPTCHA_EXPIRY_MINUTES)
        );
        captchaStore.put(token, captchaData);

        log.debug(" Generated CAPTCHA - Token: {} (expires in {} minutes)",
                token.substring(0, 8) + "...", CAPTCHA_EXPIRY_MINUTES);

        // Clean expired CAPTCHAs
        cleanExpiredCaptchas();

        return Map.of(
                "captchaText", captchaText,
                "token", token
        );
    }

    /**
     * Verify CAPTCHA answer
     * @param token CAPTCHA token
     * @param userAnswer User's answer (case-insensitive)
     * @return true if valid, false otherwise
     */
    public boolean verifyCaptcha(String token, String userAnswer) {
        if (token == null || userAnswer == null || userAnswer.trim().isEmpty()) {
            log.warn(" CAPTCHA verification failed - Invalid input");
            return false;
        }

        CaptchaData data = captchaStore.get(token);

        if (data == null) {
            log.warn(" CAPTCHA verification failed - Token not found or already used");
            return false;
        }

        // Check if CAPTCHA has expired
        if (data.expiresAt.isBefore(LocalDateTime.now())) {
            captchaStore.remove(token);
            log.warn(" CAPTCHA verification failed - Token expired");
            return false;
        }

        // Remove token after verification (one-time use)
        captchaStore.remove(token);

        // Case-insensitive comparison
        boolean isValid = data.answer.equalsIgnoreCase(userAnswer.trim());

        if (isValid) {
            log.info(" CAPTCHA verified successfully");
        } else {
            log.warn(" CAPTCHA verification failed - Incorrect answer");
        }

        return isValid;
    }

    /**
     * Generate alphanumeric CAPTCHA text
     * @return Random alphanumeric string
     */
    private String generateAlphanumericCaptcha() {
        StringBuilder captcha = new StringBuilder(CAPTCHA_LENGTH);

        for (int i = 0; i < CAPTCHA_LENGTH; i++) {
            int index = random.nextInt(CAPTCHA_CHARS.length());
            captcha.append(CAPTCHA_CHARS.charAt(index));
        }

        return captcha.toString();
    }

    /**
     * Generate secure random token for CAPTCHA identification
     * @return Hexadecimal token string
     */
    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return bytesToHex(bytes);
    }

    /**
     * Convert byte array to hexadecimal string
     * @param bytes Byte array
     * @return Hex string
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    /**
     * Clean expired CAPTCHAs from store
     * Called manually and by scheduled task
     */
    private void cleanExpiredCaptchas() {
        LocalDateTime now = LocalDateTime.now();
        int removedCount = 0;

        for (Map.Entry<String, CaptchaData> entry : captchaStore.entrySet()) {
            if (entry.getValue().expiresAt.isBefore(now)) {
                captchaStore.remove(entry.getKey());
                removedCount++;
            }
        }

        if (removedCount > 0) {
            log.debug("[CLEAN] Cleaned {} expired CAPTCHA(s)", removedCount);
        }
    }

    /**
     * Scheduled task to clean expired CAPTCHAs every 10 minutes
     */
    @Scheduled(fixedRate = 600000) // 10 minutes in milliseconds
    public void scheduledCleanup() {
        int sizeBefore = captchaStore.size();
        cleanExpiredCaptchas();
        int sizeAfter = captchaStore.size();

        if (sizeBefore != sizeAfter) {
            log.info("[CLEAN] Scheduled CAPTCHA cleanup - Removed: {}, Remaining: {}",
                    (sizeBefore - sizeAfter), sizeAfter);
        }
    }

    /**
     * Get current count of active CAPTCHAs in store
     * @return Number of active CAPTCHAs
     */
    public int getActiveCaptchaCount() {
        cleanExpiredCaptchas();
        return captchaStore.size();
    }

    /**
     * Clear all CAPTCHAs from store (useful for testing)
     */
    public void clearAllCaptchas() {
        int count = captchaStore.size();
        captchaStore.clear();
        log.info("[DELETE] Cleared all {} CAPTCHA(s) from store", count);
    }

    /**
     * Check if a token exists and is valid
     * @param token CAPTCHA token
     * @return true if token exists and not expired
     */
    public boolean isTokenValid(String token) {
        if (token == null) {
            return false;
        }

        CaptchaData data = captchaStore.get(token);
        if (data == null) {
            return false;
        }

        return data.expiresAt.isAfter(LocalDateTime.now());
    }

    /**
     * Inner class to store CAPTCHA data
     */
    private static class CaptchaData {
        String answer;
        LocalDateTime expiresAt;

        CaptchaData(String answer, LocalDateTime expiresAt) {
            this.answer = answer;
            this.expiresAt = expiresAt;
        }
    }
}