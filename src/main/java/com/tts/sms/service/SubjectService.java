package com.tts.sms.service;

import com.tts.sms.dto.SubjectDTO;
import com.tts.sms.model.Course;
import com.tts.sms.model.Subject;
import com.tts.sms.repository.CourseRepository;
import com.tts.sms.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SubjectService {

    private final SubjectRepository subjectRepository;
    private final CourseRepository courseRepository;

    /**
     * Get all subjects by course ID
     */
    @Transactional(readOnly = true)
    public List<SubjectDTO> getSubjectsByCourseId(Long courseId) {
        return subjectRepository.findByCourseIdAndIsActiveTrue(courseId)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get subject by ID
     */
    @Transactional(readOnly = true)
    public SubjectDTO getSubjectById(Long id) {
        Subject subject = subjectRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new RuntimeException("Subject not found with id: " + id));
        return convertToDTO(subject);
    }

    /**
     * Create new subject
     */
    public SubjectDTO createSubject(String subjectName, Long courseId) {
        // Validate course exists
        Course course = courseRepository.findByIdAndIsActiveTrue(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found with id: " + courseId));

        // Validate subject name uniqueness for this course
        if (subjectRepository.existsBySubjectNameAndCourseId(subjectName, courseId)) {
            throw new RuntimeException("Subject already exists for this course: " + subjectName);
        }

        Subject subject = new Subject();
        subject.setSubjectName(subjectName);
        subject.setCourse(course);
        subject.setIsActive(true);

        Subject savedSubject = subjectRepository.save(subject);
        log.info("Subject created successfully: {} for course: {}",
                savedSubject.getSubjectName(), course.getCourseName());
        return convertToDTO(savedSubject);
    }

    /**
     * Update subject
     */
    public SubjectDTO updateSubject(Long id, String subjectName, Long courseId) {
        Subject subject = subjectRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new RuntimeException("Subject not found with id: " + id));

        // Validate course exists
        Course course = courseRepository.findByIdAndIsActiveTrue(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found with id: " + courseId));

        // Validate subject name uniqueness (excluding current subject)
        if (subjectRepository.existsBySubjectNameAndCourseIdExcludingId(subjectName, courseId, id)) {
            throw new RuntimeException("Subject already exists for this course: " + subjectName);
        }

        subject.setSubjectName(subjectName);
        subject.setCourse(course);

        Subject updatedSubject = subjectRepository.save(subject);
        log.info("Subject updated successfully: {}", updatedSubject.getSubjectName());
        return convertToDTO(updatedSubject);
    }

    /**
     * Delete subject (soft delete)
     */
    public void deleteSubject(Long id) {
        Subject subject = subjectRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new RuntimeException("Subject not found with id: " + id));

        subject.setIsActive(false);
        subjectRepository.save(subject);
        log.info("Subject deleted successfully: {}", subject.getSubjectName());
    }

    /**
     * Count subjects by course
     */
    @Transactional(readOnly = true)
    public long countSubjectsByCourse(Long courseId) {
        return subjectRepository.countByCourseIdAndIsActiveTrue(courseId);
    }

    /**
     * Convert entity to DTO
     */
    private SubjectDTO convertToDTO(Subject subject) {
        SubjectDTO dto = new SubjectDTO();
        dto.setId(subject.getId());
        dto.setSubjectName(subject.getSubjectName());
        dto.setCourseId(subject.getCourse().getId());
        dto.setCourseName(subject.getCourse().getCourseName());
        dto.setIsActive(subject.getIsActive());
        dto.setCreatedAt(subject.getCreatedAt());
        dto.setUpdatedAt(subject.getUpdatedAt());
        return dto;
    }
}