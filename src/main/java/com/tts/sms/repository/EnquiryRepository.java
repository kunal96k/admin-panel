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
     * Basic finders
     */
    List<Enquiry> findByMobile(String mobile);

    List<Enquiry> findByEmailIgnoreCase(String email);

    List<Enquiry> findByStatusIgnoreCase(String status);

    /**
     * Active (non-deleted) enquiries with pagination
     */
    Page<Enquiry> findByIsDeletedFalse(Pageable pageable);

    /**
     * Find by mobile/email + not deleted
     */
    Optional<Enquiry> findByMobileAndIsDeletedFalse(String mobile);

    Optional<Enquiry> findByEmailAndIsDeletedFalse(String email);

    boolean existsByMobileAndIsDeletedFalse(String mobile);

    /**
     * Find by status/source with non-deleted constraint
     */
    List<Enquiry> findByStatusAndIsDeletedFalse(String status);

    List<Enquiry> findBySourceAndIsDeletedFalse(String source);

    /**
     * Find by course - FIXED using native query with JSON function
     */
    @Query(value = "SELECT * FROM enquiries e WHERE e.is_deleted = false " +
            "AND JSON_CONTAINS(e.courses, JSON_QUOTE(:course))",
            nativeQuery = true)
    List<Enquiry> findByCourse(@Param("course") String course);

    /**
     * Search enquiries by name, mobile, or email
     */
    @Query("SELECT e FROM Enquiry e WHERE e.isDeleted = false AND " +
            "(" +
            "LOWER(e.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(e.lastName)  LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(e.fullName)  LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "e.mobile           LIKE CONCAT('%', :search, '%') OR " +
            "LOWER(e.email)     LIKE LOWER(CONCAT('%', :search, '%'))" +
            ")")
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
     * Count total non-deleted enquiries
     */
    @Query("SELECT COUNT(e) FROM Enquiry e WHERE e.isDeleted = false")
    Long countTotalEnquiries();

    /**
     * Find enquiries assigned to a specific person
     */
    @Query("SELECT e FROM Enquiry e WHERE e.isDeleted = false AND " +
            "LOWER(e.assignTo) = LOWER(:assignTo)")
    Page<Enquiry> findByAssignTo(@Param("assignTo") String assignTo, Pageable pageable);

    /**
     * Pending follow-ups
     */
    @Query("SELECT e FROM Enquiry e WHERE e.isDeleted = false " +
            "AND e.followupDate IS NOT NULL " +
            "AND e.followupDate <= :date " +
            "AND e.status NOT IN ('Closed', 'Admitted')")
    List<Enquiry> findPendingFollowups(@Param("date") LocalDate date);

    /**
     * Advanced search with multiple filters  using native query for JSON
     */
    @Query(value = "SELECT e.* FROM enquiries e WHERE e.is_deleted = false " +
            "AND (:status IS NULL OR LOWER(e.status) = LOWER(:status)) " +
            "AND (:source IS NULL OR LOWER(e.source) = LOWER(:source)) " +
            "AND (:course IS NULL OR JSON_CONTAINS(e.courses, JSON_QUOTE(:course))) " +
            "AND (:assignTo IS NULL OR LOWER(e.assign_to) = LOWER(:assignTo)) " +
            "AND (:search IS NULL OR " +
            "    LOWER(e.first_name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "    LOWER(e.last_name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "    LOWER(e.full_name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "    e.mobile LIKE CONCAT('%', :search, '%') OR " +
            "    LOWER(e.email) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "",
            countQuery = "SELECT COUNT(*) FROM enquiries e WHERE e.is_deleted = false " +
                    "AND (:status IS NULL OR LOWER(e.status) = LOWER(:status)) " +
                    "AND (:source IS NULL OR LOWER(e.source) = LOWER(:source)) " +
                    "AND (:course IS NULL OR JSON_CONTAINS(e.courses, JSON_QUOTE(:course))) " +
                    "AND (:assignTo IS NULL OR LOWER(e.assign_to) = LOWER(:assignTo)) " +
                    "AND (:search IS NULL OR " +
                    "    LOWER(e.first_name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
                    "    LOWER(e.last_name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
                    "    LOWER(e.full_name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
                    "    e.mobile LIKE CONCAT('%', :search, '%') OR " +
                    "    LOWER(e.email) LIKE LOWER(CONCAT('%', :search, '%')))",
            nativeQuery = true)
    Page<Enquiry> advancedSearch(
            @Param("search") String search,
            @Param("status") String status,
            @Param("source") String source,
            @Param("course") String course,
            @Param("assignTo") String assignTo,
            Pageable pageable
    );

    /**
     * Dashboard statistics
     */
    @Query("SELECT e.status, COUNT(e) FROM Enquiry e " +
            "WHERE e.isDeleted = false GROUP BY e.status")
    List<Object[]> getEnquiryStatsByStatus();

    @Query("SELECT e.source, COUNT(e) FROM Enquiry e " +
            "WHERE e.isDeleted = false GROUP BY e.source")
    List<Object[]> getEnquiryStatsBySource();

    /**
     * Recent enquiries
     */
    @Query("SELECT e FROM Enquiry e WHERE e.isDeleted = false " +
            "ORDER BY e.enquiryDate DESC")
    List<Enquiry> findRecentEnquiries(Pageable pageable);
}