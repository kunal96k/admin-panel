package com.tts.sms.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.tts.sms.model.ManualCertificateLog;

@Repository
public interface ManualCertificateLogRepository extends JpaRepository<ManualCertificateLog, Long> {

    /**
     * Find all logs ordered by creation date
     */
    List<ManualCertificateLog> findAllByIsActiveTrueOrderByCreatedAtDesc();

    Page<ManualCertificateLog> findAllByIsActiveTrueOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Find logs by registration number
     */
    List<ManualCertificateLog> findByRegistrationNoAndIsActiveTrueOrderByCreatedAtDesc(String registrationNo);

    /**
     * Find logs by employee
     */
    List<ManualCertificateLog> findByCreatedByEmployeeIdAndIsActiveTrueOrderByCreatedAtDesc(Long employeeId);
}