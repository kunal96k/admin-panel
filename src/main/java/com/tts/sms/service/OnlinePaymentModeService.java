package com.tts.sms.service;

import com.tts.sms.dto.OnlinePaymentModeDTO;
import com.tts.sms.exception.DuplicateResourceException;
import com.tts.sms.exception.ResourceNotFoundException;
import com.tts.sms.model.OnlinePaymentMode;
import com.tts.sms.repository.OnlinePaymentModeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OnlinePaymentModeService {

    private final OnlinePaymentModeRepository paymentModeRepository;

    /**
     * Get all payment modes
     */
    @Transactional(readOnly = true)
    public List<OnlinePaymentModeDTO> getAllPaymentModes() {
        log.info("Fetching all payment modes");
        return paymentModeRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get all active payment modes
     */
    @Transactional(readOnly = true)
    public List<OnlinePaymentModeDTO> getActivePaymentModes() {
        log.info("Fetching active payment modes");
        return paymentModeRepository.findByIsActiveTrue().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get payment mode by ID
     */
    @Transactional(readOnly = true)
    public OnlinePaymentModeDTO getPaymentModeById(Long id) {
        log.info("Fetching payment mode with ID: {}", id);
        OnlinePaymentMode paymentMode = paymentModeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment mode not found with ID: " + id));
        return convertToDTO(paymentMode);
    }

    /**
     * Create new payment mode
     */
    @Transactional
    public OnlinePaymentModeDTO createPaymentMode(OnlinePaymentModeDTO paymentModeDTO) {
        log.info("Creating new payment mode: {}", paymentModeDTO.getPaymentModeTitle());

        // Validate input
        validatePaymentModeTitle(paymentModeDTO.getPaymentModeTitle());

        // Check for duplicates
        if (paymentModeRepository.existsByPaymentModeTitleIgnoreCase(paymentModeDTO.getPaymentModeTitle().trim())) {
            throw new DuplicateResourceException("Payment mode with title '" + paymentModeDTO.getPaymentModeTitle() + "' already exists");
        }

        OnlinePaymentMode paymentMode = convertToEntity(paymentModeDTO);
        paymentMode.setPaymentModeTitle(paymentModeDTO.getPaymentModeTitle().trim());
        paymentMode.setIsActive(true);

        OnlinePaymentMode savedPaymentMode = paymentModeRepository.save(paymentMode);
        log.info("Payment mode created successfully with ID: {}", savedPaymentMode.getId());

        return convertToDTO(savedPaymentMode);
    }

    /**
     * Update payment mode
     */
    @Transactional
    public OnlinePaymentModeDTO updatePaymentMode(Long id, OnlinePaymentModeDTO paymentModeDTO) {
        log.info("Updating payment mode with ID: {}", id);

        OnlinePaymentMode existingPaymentMode = paymentModeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment mode not found with ID: " + id));

        // Validate input
        validatePaymentModeTitle(paymentModeDTO.getPaymentModeTitle());

        // Check for duplicates (excluding current record)
        if (paymentModeRepository.existsByPaymentModeTitleIgnoreCaseAndIdNot(
                paymentModeDTO.getPaymentModeTitle().trim(), id)) {
            throw new DuplicateResourceException("Payment mode with title '" + paymentModeDTO.getPaymentModeTitle() + "' already exists");
        }

        existingPaymentMode.setPaymentModeTitle(paymentModeDTO.getPaymentModeTitle().trim());

        OnlinePaymentMode updatedPaymentMode = paymentModeRepository.save(existingPaymentMode);
        log.info("Payment mode updated successfully with ID: {}", updatedPaymentMode.getId());

        return convertToDTO(updatedPaymentMode);
    }

    /**
     * Delete payment mode (hard delete)
     */
    @Transactional
    public void deletePaymentMode(Long id) {
        log.info("Deleting payment mode with ID: {}", id);

        OnlinePaymentMode paymentMode = paymentModeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment mode not found with ID: " + id));

        paymentModeRepository.delete(paymentMode);
        log.info("Payment mode deleted successfully with ID: {}", id);
    }

    /**
     * Validate payment mode title
     */
    private void validatePaymentModeTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Payment mode title cannot be empty");
        }

        String trimmedTitle = title.trim();

        if (trimmedTitle.length() < 2) {
            throw new IllegalArgumentException("Payment mode title must be at least 2 characters long");
        }

        if (trimmedTitle.length() > 500) {
            throw new IllegalArgumentException("Payment mode title must not exceed 500 characters");
        }
    }

    /**
     * Convert entity to DTO
     */
    private OnlinePaymentModeDTO convertToDTO(OnlinePaymentMode paymentMode) {
        OnlinePaymentModeDTO dto = new OnlinePaymentModeDTO();
        dto.setId(paymentMode.getId());
        dto.setPaymentModeTitle(paymentMode.getPaymentModeTitle());
        dto.setIsActive(paymentMode.getIsActive());
        dto.setCreatedDate(paymentMode.getCreatedDate());
        dto.setUpdatedDate(paymentMode.getUpdatedDate());
        return dto;
    }

    /**
     * Convert DTO to entity
     */
    private OnlinePaymentMode convertToEntity(OnlinePaymentModeDTO dto) {
        OnlinePaymentMode paymentMode = new OnlinePaymentMode();
        paymentMode.setId(dto.getId());
        paymentMode.setPaymentModeTitle(dto.getPaymentModeTitle());
        paymentMode.setIsActive(dto.getIsActive());
        return paymentMode;
    }
}