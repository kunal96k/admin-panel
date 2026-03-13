package com.tts.sms.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.tts.sms.model.OnlinePaymentMode;

@Repository
public interface OnlinePaymentModeRepository extends JpaRepository<OnlinePaymentMode, Long> {

    /**
     * Find all active payment modes
     */
    List<OnlinePaymentMode> findByIsActiveTrue();

    Page<OnlinePaymentMode> findByIsActiveTrue(Pageable pageable);

    /**
     * Check if payment mode title exists (case-insensitive)
     */
    boolean existsByPaymentModeTitleIgnoreCase(String paymentModeTitle);

    /**
     * Check if payment mode title exists excluding a specific ID (for updates)
     */
    boolean existsByPaymentModeTitleIgnoreCaseAndIdNot(String paymentModeTitle, Long id);

    /**
     * Find by payment mode title (case-insensitive)
     */
    Optional<OnlinePaymentMode> findByPaymentModeTitleIgnoreCase(String paymentModeTitle);
}