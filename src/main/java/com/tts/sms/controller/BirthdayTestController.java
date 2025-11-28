package com.tts.sms.controller;

import com.tts.sms.scheduler.BirthdayEmailScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Test controller for manually triggering birthday emails
 * Useful for testing without waiting for scheduled time
 */
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class BirthdayTestController {

    private final BirthdayEmailScheduler birthdayEmailScheduler;

    /**
     * Manually trigger birthday email sending
     * GET /api/test/send-birthday-wishes
     */
    @GetMapping("/send-birthday-wishes")
    public ResponseEntity<Map<String, String>> sendBirthdayWishesManually() {
        birthdayEmailScheduler.sendBirthdayWishesManually();

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Birthday wishes process completed. Check logs for details.");

        return ResponseEntity.ok(response);
    }
}