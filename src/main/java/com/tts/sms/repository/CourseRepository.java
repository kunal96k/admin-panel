package com.tts.sms.repository;

import com.tts.sms.model.Course;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {

    /**
     * Find all active courses
     */
    List<Course> findByIsActiveTrue();

    /**
     * Find course by ID and active status
     */
    Optional<Course> findByIdAndIsActiveTrue(Long id);

    /**
     * Search courses by name with pagination
     */
    @Query("SELECT c FROM Course c WHERE " +
            "LOWER(c.courseName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "AND c.isActive = :isActive " +
            "ORDER BY c.courseName ASC")
    Page<Course> searchCourses(
            @Param("searchTerm") String searchTerm,
            @Param("isActive") Boolean isActive,
            Pageable pageable
    );

    /**
     * Find all courses with pagination
     */
    Page<Course> findByIsActiveTrue(Pageable pageable);

    /**
     * Check if course name already exists
     */
    boolean existsByCourseNameIgnoreCaseAndIsActiveTrue(String courseName);

    /**
     * Check if course name exists excluding specific ID
     */
    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM Course c " +
            "WHERE LOWER(c.courseName) = LOWER(:courseName) " +
            "AND c.id != :courseId " +
            "AND c.isActive = true")
    boolean existsByCourseNameExcludingId(
            @Param("courseName") String courseName,
            @Param("courseId") Long courseId
    );

    /**
     * Count active courses
     */
    long countByIsActiveTrue();
}