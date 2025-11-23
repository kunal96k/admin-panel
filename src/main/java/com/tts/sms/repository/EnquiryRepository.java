package com.tts.sms.repository;

import com.tts.sms.model.Enquiry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface EnquiryRepository extends JpaRepository<Enquiry, Long>,
        JpaSpecificationExecutor<Enquiry> {

    /**
     * Find enquiries by mobile number
     */
    List<Enquiry> findByMobile(String mobile);

    /**
     * Find enquiries by email
     */
    List<Enquiry> findByEmailIgnoreCase(String email);

    /**
     * Find enquiries by status
     */
    List<Enquiry> findByStatusIgnoreCase(String status);

    /**
     * Find active (non-deleted) enquiries
     */
    Page<Enquiry> findByIsDeletedFalse(Pageable pageable);

    /**
     * Search enquiries by name, mobile, or email
     */
    @Query("SELECT e FROM Enquiry e WHERE e.isDeleted = false AND " +
            "(LOWER(e.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(e.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(e.fullName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "e.mobile LIKE CONCAT('%', :search, '%') OR " +
            "LOWER(e.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Enquiry> searchEnquiries(@Param("search") String search, Pageable pageable);

    /**
     * Find enquiries by date range
     */
    @Query("SELECT e FROM Enquiry e WHERE e.isDeleted = false AND " +
            "e.enquiryDate BETWEEN :fromDate AND :toDate")
    List<Enquiry> findByEnquiryDateBetween(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    /**
     * Count enquiries by status
     */
    @Query("SELECT COUNT(e) FROM Enquiry e WHERE e.isDeleted = false AND " +
            "LOWER(e.status) = LOWER(:status)")
    long countByStatus(@Param("status") String status);

    /**
     * Count enquiries by source
     */
    @Query("SELECT COUNT(e) FROM Enquiry e WHERE e.isDeleted = false AND " +
            "LOWER(e.source) = LOWER(:source)")
    long countBySource(@Param("source") String source);

    /**
     * Find enquiries assigned to a specific person
     */
    @Query("SELECT e FROM Enquiry e WHERE e.isDeleted = false AND " +
            "LOWER(e.assignTo) = LOWER(:assignTo)")
    Page<Enquiry> findByAssignTo(@Param("assignTo") String assignTo, Pageable pageable);

    /**
     * Find enquiries with pending follow-up
     */
    @Query("SELECT e FROM Enquiry e WHERE e.isDeleted = false AND " +
            "e.followupDate IS NOT NULL AND e.followupDate <= :date AND " +
            "LOWER(e.status) NOT IN ('converted', 'closed')")
    List<Enquiry> findPendingFollowups(@Param("date") LocalDate date);

    /**
     * Advanced search with multiple filters
     */
    @Query("SELECT e FROM Enquiry e WHERE e.isDeleted = false " +
            "AND (:status IS NULL OR LOWER(e.status) = LOWER(:status)) " +
            "AND (:source IS NULL OR LOWER(e.source) = LOWER(:source)) " +
            "AND (:course IS NULL OR LOWER(e.course) LIKE LOWER(CONCAT('%', :course, '%'))) " +
            "AND (:assignTo IS NULL OR LOWER(e.assignTo) = LOWER(:assignTo)) " +
            "AND (:search IS NULL OR " +
            "    LOWER(e.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "    LOWER(e.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "    LOWER(e.fullName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "    e.mobile LIKE CONCAT('%', :search, '%'))")
    Page<Enquiry> advancedSearch(
            @Param("search") String search,
            @Param("status") String status,
            @Param("source") String source,
            @Param("course") String course,
            @Param("assignTo") String assignTo,
            Pageable pageable
    );

    /**
     * Check if mobile number exists
     */
    boolean existsByMobileAndIsDeletedFalse(String mobile);

    /**
     * Get statistics for dashboard
     */
    @Query("SELECT e.status, COUNT(e) FROM Enquiry e " +
            "WHERE e.isDeleted = false GROUP BY e.status")
    List<Object[]> getEnquiryStatsByStatus();

    @Query("SELECT e.source, COUNT(e) FROM Enquiry e " +
            "WHERE e.isDeleted = false GROUP BY e.source")
    List<Object[]> getEnquiryStatsBySource();
}