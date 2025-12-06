package com.tts.sms.repository;

import com.tts.sms.model.Batch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BatchRepository extends JpaRepository<Batch, Long> {

    // Find by batch number
    Optional<Batch> findByBatchNo(String batchNo);

    // Check if batch number exists
    boolean existsByBatchNo(String batchNo);

    List<Batch> findByIsActiveTrueOrderByBatchNameAsc();


    // Find all active batches
    Page<Batch> findByIsActiveTrueOrderByCreatedDateDesc(Pageable pageable);

    // Find by status
    Page<Batch> findByStatusAndIsActiveTrueOrderByCreatedDateDesc(String status, Pageable pageable);

    // Find by course
    Page<Batch> findByCourseIdAndIsActiveTrueOrderByCreatedDateDesc(Long courseId, Pageable pageable);

    // Search batches
    @Query("SELECT b FROM Batch b WHERE b.isActive = true AND " +
            "(LOWER(b.batchName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(b.batchNo) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(b.status) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
            "ORDER BY b.createdDate DESC")
    Page<Batch> searchBatches(@Param("searchTerm") String searchTerm, Pageable pageable);

    // Advanced search with filters
    @Query("SELECT b FROM Batch b WHERE b.isActive = true " +
            "AND (:searchTerm IS NULL OR " +
            "LOWER(b.batchName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(b.batchNo) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
            "AND (:status IS NULL OR b.status = :status) " +
            "AND (:courseId IS NULL OR b.course.id = :courseId) " +
            "ORDER BY b.createdDate DESC")
    Page<Batch> searchWithFilters(
            @Param("searchTerm") String searchTerm,
            @Param("status") String status,
            @Param("courseId") Long courseId,
            Pageable pageable
    );

    // Get next batch number - FIXED VERSION using native query
    @Query(value = "SELECT COALESCE(MAX(CAST(batch_no AS UNSIGNED)), 0) " +
            "FROM batches " +
            "WHERE batch_no REGEXP '^[0-9]+$'",
            nativeQuery = true)
    Integer findMaxBatchNumber();

    @Query("SELECT b.batchNo FROM Batch b WHERE b.isActive = true")
    List<String> findAllBatchNumbers();

    // Count active batches
    long countByIsActiveTrue();

    // Count batches by course
    long countByCourseIdAndIsActiveTrue(Long courseId);

    // Find batches by multiple statuses
    @Query("SELECT b FROM Batch b WHERE b.isActive = true AND b.status IN :statuses ORDER BY b.createdDate DESC")
    List<Batch> findByStatusIn(@Param("statuses") List<String> statuses);

    // Find overlapping batches (same time slot)
    @Query("SELECT b FROM Batch b WHERE b.isActive = true " +
            "AND (:batchId IS NULL OR b.id != :batchId) " +
            "AND b.startTime < :endTime " +
            "AND b.endTime > :startTime " +
            "AND ((:isSunday = true AND b.isSunday = true) OR " +
            "(:isMonday = true AND b.isMonday = true) OR " +
            "(:isTuesday = true AND b.isTuesday = true) OR " +
            "(:isWednesday = true AND b.isWednesday = true) OR " +
            "(:isThursday = true AND b.isThursday = true) OR " +
            "(:isFriday = true AND b.isFriday = true) OR " +
            "(:isSaturday = true AND b.isSaturday = true))")
    List<Batch> findOverlappingBatches(
            @Param("batchId") Long batchId,
            @Param("startTime") java.time.LocalTime startTime,
            @Param("endTime") java.time.LocalTime endTime,
            @Param("isSunday") Boolean isSunday,
            @Param("isMonday") Boolean isMonday,
            @Param("isTuesday") Boolean isTuesday,
            @Param("isWednesday") Boolean isWednesday,
            @Param("isThursday") Boolean isThursday,
            @Param("isFriday") Boolean isFriday,
            @Param("isSaturday") Boolean isSaturday
    );
}