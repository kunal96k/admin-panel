package com.tts.sms.service;

import com.tts.sms.dto.*;
import com.tts.sms.model.Course;
import com.tts.sms.model.Subject;
import com.tts.sms.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CourseService {

    private final CourseRepository courseRepository;
    private final FileStorageService fileStorageService;

    /**
     * Get all active courses
     */
    @Transactional(readOnly = true)
    public List<CourseDTO> getAllCourses() {
        return courseRepository.findByIsActiveTrue()
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get courses with pagination
     */
    @Transactional(readOnly = true)
    public Page<CourseDTO> getCoursesPaginated(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return courseRepository.findByIsActiveTrue(pageable)
                .map(this::convertToDTO);
    }

    /**
     * Search courses with pagination
     */
    @Transactional(readOnly = true)
    public Page<CourseDTO> searchCourses(String searchTerm, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return courseRepository.searchCourses(searchTerm, true, pageable)
                .map(this::convertToDTO);
    }

    /**
     * Get course by ID
     */
    @Transactional(readOnly = true)
    public CourseDTO getCourseById(Long id) {
        Course course = courseRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new RuntimeException("Course not found with id: " + id));
        return convertToDTO(course);
    }

    /**
     * Create new course
     */
    public CourseDTO createCourse(String courseName, BigDecimal courseFees, MultipartFile imageFile) {
        // Validate course name uniqueness
        if (courseRepository.existsByCourseNameIgnoreCaseAndIsActiveTrue(courseName)) {
            throw new RuntimeException("Course name already exists: " + courseName);
        }

        Course course = new Course();
        course.setCourseName(courseName);
        course.setCourseFees(courseFees);
        course.setIsActive(true);

        // Handle image upload
        if (imageFile != null && !imageFile.isEmpty()) {
            validateImageFile(imageFile);
            String filename = fileStorageService.storeFile(imageFile, "courses");
            course.setCourseImagePath(filename);
        }

        Course savedCourse = courseRepository.save(course);
        log.info("Course created successfully: {}", savedCourse.getCourseName());
        return convertToDTO(savedCourse);
    }

    /**
     * Update course
     */
    public CourseDTO updateCourse(Long id, String courseName, BigDecimal courseFees, MultipartFile imageFile) {
        Course course = courseRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new RuntimeException("Course not found with id: " + id));

        // Validate course name uniqueness (excluding current course)
        if (courseRepository.existsByCourseNameExcludingId(courseName, id)) {
            throw new RuntimeException("Course name already exists: " + courseName);
        }

        course.setCourseName(courseName);
        course.setCourseFees(courseFees);

        // Handle image upload
        if (imageFile != null && !imageFile.isEmpty()) {
            validateImageFile(imageFile);

            // Delete old image
            if (course.getCourseImagePath() != null) {
                fileStorageService.deleteFile(course.getCourseImagePath(), "courses");
            }

            String filename = fileStorageService.storeFile(imageFile, "courses");
            course.setCourseImagePath(filename);
        }

        Course updatedCourse = courseRepository.save(course);
        log.info("Course updated successfully: {}", updatedCourse.getCourseName());
        return convertToDTO(updatedCourse);
    }

    /**
     * Delete course (soft delete)
     */
    public void deleteCourse(Long id) {
        Course course = courseRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new RuntimeException("Course not found with id: " + id));

        course.setIsActive(false);
        courseRepository.save(course);
        log.info("Course deleted successfully: {}", course.getCourseName());
    }

    /**
     * Export courses to CSV format
     */
    @Transactional(readOnly = true)
    public List<CourseCSVExportDTO> exportCoursesToCSV() {
        List<Course> courses = courseRepository.findByIsActiveTrue();
        return courses.stream()
                .map((Course course) -> {
                    int index = courses.indexOf(course) + 1;
                    return new CourseCSVExportDTO(
                            index,
                            course.getCourseName(),
                            course.getCourseFees()
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * Validate image file
     */
    private void validateImageFile(MultipartFile file) {
        if (!fileStorageService.isValidImageFile(file)) {
            throw new RuntimeException("Invalid file type. Only JPG and PNG images are allowed.");
        }
        if (!fileStorageService.isValidFileSize(file)) {
            throw new RuntimeException("File size exceeds maximum limit of 2MB.");
        }
    }

    /**
     * Convert entity to DTO
     */
    private CourseDTO convertToDTO(Course course) {
        CourseDTO dto = new CourseDTO();
        dto.setId(course.getId());
        dto.setCourseName(course.getCourseName());
        dto.setCourseFees(course.getCourseFees());
        dto.setCourseImagePath(course.getCourseImagePath());
        dto.setIsActive(course.getIsActive());
        dto.setCreatedAt(course.getCreatedAt());
        dto.setUpdatedAt(course.getUpdatedAt());

        // Convert subjects
        if (course.getSubjects() != null) {
            List<SubjectDTO> subjectDTOs = course.getSubjects().stream()
                    .filter(Subject::getIsActive)
                    .map(this::convertSubjectToDTO)
                    .collect(Collectors.toList());
            dto.setSubjects(subjectDTOs);
        }

        return dto;
    }

    /**
     * Convert subject entity to DTO
     */
    private SubjectDTO convertSubjectToDTO(Subject subject) {
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