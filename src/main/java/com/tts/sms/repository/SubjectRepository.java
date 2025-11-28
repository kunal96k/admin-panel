package com.tts.sms.repository;

import com.tts.sms.model.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubjectRepository extends JpaRepository<Subject, Long> {

    /**
     * Find all subjects by course ID
     */
    List<Subject> findByCourseIdAndIsActiveTrue(Long courseId);

    /**
     * Find subject by ID and active status
     */
    Optional<Subject> findByIdAndIsActiveTrue(Long id);

    /**
     * Check if subject name exists for a course
     */
    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM Subject s " +
            "WHERE LOWER(s.subjectName) = LOWER(:subjectName) " +
            "AND s.course.id = :courseId " +
            "AND s.isActive = true")
    boolean existsBySubjectNameAndCourseId(
            @Param("subjectName") String subjectName,
            @Param("courseId") Long courseId
    );

    /**
     * Check if subject name exists for a course excluding specific subject ID
     */
    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM Subject s " +
            "WHERE LOWER(s.subjectName) = LOWER(:subjectName) " +
            "AND s.course.id = :courseId " +
            "AND s.id != :subjectId " +
            "AND s.isActive = true")
    boolean existsBySubjectNameAndCourseIdExcludingId(
            @Param("subjectName") String subjectName,
            @Param("courseId") Long courseId,
            @Param("subjectId") Long subjectId
    );

    /**
     * Count subjects by course ID
     */
    long countByCourseIdAndIsActiveTrue(Long courseId);

    /**
     * Delete all subjects by course ID
     */
    void deleteByCourseId(Long courseId);
}