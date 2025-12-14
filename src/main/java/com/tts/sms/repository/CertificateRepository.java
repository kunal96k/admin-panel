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

    /**
     * Find certificates by registration number and status
     */
    List<Certificate> findByRegistrationNoAndStatusAndIsActiveTrue(
            String registrationNo,
            String status
    );

    List<Certificate> findByRegistrationNoAndIsActiveTrue(String registrationNo);
    
    // Check if registration number exists
    boolean existsByRegistrationNoAndIsActiveTrue(String registrationNo);
    
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
    
    long countByStatusAndIsActiveTrue(String status);

    boolean existsByRegistrationNoAndCourseNameAndIsActiveTrue(
            String registrationNo,
            String courseName
    );

    /**
     *  Check if certificate number exists (case-insensitive for safety)
     */
    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM Certificate c " +
            "WHERE LOWER(c.certificateNo) = LOWER(:certificateNo) AND c.isActive = true")
    boolean existsByCertificateNoAndIsActiveTrue(@Param("certificateNo") String certificateNo);

    /**
     *  Count all active certificates (for unique number generation)
     */
    long countByIsActiveTrue();

    /**
     *  Find certificate by certificate number (for validation)
     */
    @Query("SELECT c FROM Certificate c WHERE LOWER(c.certificateNo) = LOWER(:certificateNo) AND c.isActive = true")
    Optional<Certificate> findByCertificateNoAndIsActiveTrue(@Param("certificateNo") String certificateNo);

    Long countByStatus(String status);
}