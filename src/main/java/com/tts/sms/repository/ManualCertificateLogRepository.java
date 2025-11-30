package com.tts.sms.repository;

import com.tts.sms.model.ManualCertificateLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ManualCertificateLogRepository extends JpaRepository<ManualCertificateLog, Long> {

    /**
     * Find all logs ordered by creation date
     */
    List<ManualCertificateLog> findAllByIsActiveTrueOrderByCreatedAtDesc();

    /**
     * Find logs by registration number
     */
    List<ManualCertificateLog> findByRegistrationNoAndIsActiveTrueOrderByCreatedAtDesc(String registrationNo);

    /**
     * Find logs by employee
     */
    List<ManualCertificateLog> findByCreatedByEmployeeIdAndIsActiveTrueOrderByCreatedAtDesc(Long employeeId);
}