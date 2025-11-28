package com.tts.sms.controller;

import com.tts.sms.dto.OnlinePaymentModeDTO;
import com.tts.sms.service.OnlinePaymentModeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payment-modes")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class OnlinePaymentModeController {

    private final OnlinePaymentModeService paymentModeService;

    /**
     * Get all payment modes
     */
    @GetMapping
    public ResponseEntity<List<OnlinePaymentModeDTO>> getAllPaymentModes() {
        log.info("REST request to get all payment modes");
        try {
            List<OnlinePaymentModeDTO> paymentModes = paymentModeService.getAllPaymentModes();
            return ResponseEntity.ok(paymentModes);
        } catch (Exception e) {
            log.error("Error fetching payment modes: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get active payment modes
     */
    @GetMapping("/active")
    public ResponseEntity<List<OnlinePaymentModeDTO>> getActivePaymentModes() {
        log.info("REST request to get active payment modes");
        try {
            List<OnlinePaymentModeDTO> paymentModes = paymentModeService.getActivePaymentModes();
            return ResponseEntity.ok(paymentModes);
        } catch (Exception e) {
            log.error("Error fetching active payment modes: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get payment mode by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<OnlinePaymentModeDTO> getPaymentModeById(@PathVariable Long id) {
        log.info("REST request to get payment mode with ID: {}", id);
        try {
            OnlinePaymentModeDTO paymentMode = paymentModeService.getPaymentModeById(id);
            return ResponseEntity.ok(paymentMode);
        } catch (Exception e) {
            log.error("Error fetching payment mode with ID {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * Create new payment mode
     */
    @PostMapping
    public ResponseEntity<?> createPaymentMode(@Valid @RequestBody OnlinePaymentModeDTO paymentModeDTO) {
        log.info("REST request to create payment mode: {}", paymentModeDTO.getPaymentModeTitle());
        try {
            OnlinePaymentModeDTO createdPaymentMode = paymentModeService.createPaymentMode(paymentModeDTO);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdPaymentMode);
        } catch (IllegalArgumentException e) {
            log.error("Validation error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(createErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("Error creating payment mode: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to create payment mode: " + e.getMessage()));
        }
    }

    /**
     * Update payment mode
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updatePaymentMode(
            @PathVariable Long id,
            @Valid @RequestBody OnlinePaymentModeDTO paymentModeDTO) {
        log.info("REST request to update payment mode with ID: {}", id);
        try {
            OnlinePaymentModeDTO updatedPaymentMode = paymentModeService.updatePaymentMode(id, paymentModeDTO);
            return ResponseEntity.ok(updatedPaymentMode);
        } catch (IllegalArgumentException e) {
            log.error("Validation error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(createErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating payment mode with ID {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to update payment mode: " + e.getMessage()));
        }
    }

    /**
     * Delete payment mode
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePaymentMode(@PathVariable Long id) {
        log.info("REST request to delete payment mode with ID: {}", id);
        try {
            paymentModeService.deletePaymentMode(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error deleting payment mode with ID {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to delete payment mode: " + e.getMessage()));
        }
    }

    /**
     * Create error response
     */
    private Map<String, String> createErrorResponse(String message) {
        Map<String, String> error = new HashMap<>();
        error.put("message", message);
        return error;
    }
}