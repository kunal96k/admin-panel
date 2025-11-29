package com.tts.sms.repository;

import com.tts.sms.model.Certificate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CertificateRepository extends JpaRepository<Certificate, Long> {

    List<Certificate> findByRegistrationNoAndIsActiveTrue(String registrationNo);

    // Find by certificate number
    Optional<Certificate> findByCertificateNoAndIsActiveTrue(String certificateNo);

    // Check if registration number exists
    boolean existsByRegistrationNoAndIsActiveTrue(String registrationNo);

    // Check if certificate number exists
    boolean existsByCertificateNoAndIsActiveTrue(String certificateNo);

    // Get all active certificates with pagination
    Page<Certificate> findByIsActiveTrue(Pageable pageable);

    // Get all active certificates (for export)
    List<Certificate> findByIsActiveTrue();

    // Filter by course
    Page<Certificate> findByCourseNameAndIsActiveTrue(String courseName, Pageable pageable);

    // Filter by status
    Page<Certificate> findByStatusAndIsActiveTrue(String status, Pageable pageable);

    // Filter by course and status
    Page<Certificate> findByCourseNameAndStatusAndIsActiveTrue(
            String courseName, String status, Pageable pageable);

    // Search certificates
    @Query("SELECT c FROM Certificate c WHERE c.isActive = true AND " +
            "(LOWER(c.registrationNo) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(c.certificateNo) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(c.studentName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(c.courseName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(c.batch) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Certificate> searchCertificates(@Param("search") String search, Pageable pageable);

    // Search with course filter
    @Query("SELECT c FROM Certificate c WHERE c.isActive = true AND " +
            "c.courseName = :courseName AND " +
            "(LOWER(c.registrationNo) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(c.certificateNo) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(c.studentName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(c.batch) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Certificate> searchCertificatesByCourse(
            @Param("search") String search,
            @Param("courseName") String courseName,
            Pageable pageable);

    // Search with status filter
    @Query("SELECT c FROM Certificate c WHERE c.isActive = true AND " +
            "c.status = :status AND " +
            "(LOWER(c.registrationNo) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(c.certificateNo) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(c.studentName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(c.batch) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Certificate> searchCertificatesByStatus(
            @Param("search") String search,
            @Param("status") String status,
            Pageable pageable);

    // Search with both filters
    @Query("SELECT c FROM Certificate c WHERE c.isActive = true AND " +
            "c.courseName = :courseName AND c.status = :status AND " +
            "(LOWER(c.registrationNo) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(c.certificateNo) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(c.studentName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(c.batch) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Certificate> searchCertificatesByCourseAndStatus(
            @Param("search") String search,
            @Param("courseName") String courseName,
            @Param("status") String status,
            Pageable pageable);

    // Count statistics
    long countByIsActiveTrue();
    long countByStatusAndIsActiveTrue(String status);
}