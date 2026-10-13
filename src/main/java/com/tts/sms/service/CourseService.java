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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CourseService {

    private final CourseRepository courseRepository;
    private final FileStorageService fileStorageService;

    @Value("${app.upload.courses.brochures-dir:./uploads/courses/brochures}")
    private String brochuresDir;

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
     * Create new course (backward-compatible overload)
     */
    public CourseDTO createCourse(String courseName, BigDecimal courseFees, MultipartFile imageFile) {
        return createCourse(courseName, courseFees, imageFile, null);
    }

    /**
     * Create new course with optional brochure
     */
    public CourseDTO createCourse(String courseName, BigDecimal courseFees, MultipartFile imageFile, MultipartFile brochureFile) {
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

        // Handle brochure upload
        if (brochureFile != null && !brochureFile.isEmpty()) {
            String brochureFilename = storeBrochureFile(brochureFile, courseName);
            course.setBrochurePath(brochureFilename);
        }

        Course savedCourse = courseRepository.save(course);
        log.info("Course created successfully: {}", savedCourse.getCourseName());
        return convertToDTO(savedCourse);
    }

    /**
     * Update course (backward-compatible overload)
     */
    public CourseDTO updateCourse(Long id, String courseName, BigDecimal courseFees, MultipartFile imageFile) {
        return updateCourse(id, courseName, courseFees, imageFile, null, false);
    }

    /**
     * Update course with optional brochure and remove flag
     */
    public CourseDTO updateCourse(Long id, String courseName, BigDecimal courseFees, MultipartFile imageFile, MultipartFile brochureFile, Boolean removeBrochure) {
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

        // Handle brochure removal
        if (Boolean.TRUE.equals(removeBrochure)) {
            if (course.getBrochurePath() != null) {
                deleteBrochureFile(course.getBrochurePath());
                course.setBrochurePath(null);
            }
        }

        // Handle new brochure upload
        if (brochureFile != null && !brochureFile.isEmpty()) {
            // Delete old brochure if present
            if (course.getBrochurePath() != null) {
                deleteBrochureFile(course.getBrochurePath());
            }

            String brochureFilename = storeBrochureFile(brochureFile, courseName);
            course.setBrochurePath(brochureFilename);
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
     * Validate brochure file (PDF or ZIP, max 25MB)
     */
    private void validateBrochureFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return;
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null) {
            throw new RuntimeException("Brochure file name is invalid.");
        }
        String lower = originalName.toLowerCase();
        if (!lower.endsWith(".pdf") && !lower.endsWith(".zip")) {
            throw new RuntimeException("Invalid brochure format. Only PDF or ZIP files are allowed.");
        }
        if (file.getSize() > 25 * 1024 * 1024) {
            throw new RuntimeException("Brochure file size exceeds maximum limit of 25MB.");
        }
    }

    /**
     * Store brochure file to ./uploads/courses/brochures/
     * Stored using clean CourseName_Brochure naming convention
     */
    private String storeBrochureFile(MultipartFile file, String courseName) {
        validateBrochureFile(file);
        try {
            Path brochureDirPath = Paths.get(brochuresDir).toAbsolutePath();
            if (!Files.exists(brochureDirPath)) {
                Files.createDirectories(brochureDirPath);
                log.info("[BROCHURE] Created brochures directory: {}", brochureDirPath);
            }

            String originalFilename = file.getOriginalFilename();
            String extension = ".pdf";
            if (originalFilename != null && originalFilename.lastIndexOf(".") != -1) {
                extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
            }

            // Clean format: CourseName_Brochure.pdf
            String filename = formatBrochureFileName(courseName, extension);
            Path targetPath = brochureDirPath.resolve(filename);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("[BROCHURE] Saved brochure file: {}", targetPath);
            return filename;
        } catch (IOException e) {
            log.error("[BROCHURE] Failed to store brochure file: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to store brochure file: " + e.getMessage());
        }
    }

    /**
     * Format clean brochure filename: CourseName_Brochure.ext (e.g. Spring_Boot_Brochure.pdf)
     */
    public static String formatBrochureFileName(String courseName, String extension) {
        if (courseName == null || courseName.isBlank()) {
            return "Course_Brochure" + (extension != null ? extension : ".pdf");
        }
        String clean = courseName.trim().replaceAll("[,/\\\\&]+", " ");
        String[] words = clean.split("[\\s_]+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            String trimmed = w.replaceAll("[^a-zA-Z0-9]", "");
            if (trimmed.isBlank()) continue;
            if (sb.length() > 0) sb.append("_");
            if (trimmed.length() == 1) {
                sb.append(trimmed.toUpperCase());
            } else if (trimmed.matches("[A-Z0-9]+")) {
                sb.append(trimmed.substring(0, 1).toUpperCase()).append(trimmed.substring(1).toLowerCase());
            } else {
                sb.append(trimmed.substring(0, 1).toUpperCase()).append(trimmed.substring(1));
            }
        }
        if (sb.length() == 0) {
            sb.append("Course");
        }
        String base = sb.toString();
        if (!base.toLowerCase().contains("course")) {
            base += "_Course";
        }
        String ext = (extension != null && !extension.isBlank()) ? extension : ".pdf";
        if (!ext.startsWith(".")) ext = "." + ext;
        return base + "_Brochure" + ext;
    }

    private String getFileExtension(String fileName) {
        if (fileName != null && fileName.lastIndexOf(".") != -1) {
            return fileName.substring(fileName.lastIndexOf(".")).toLowerCase();
        }
        return ".pdf";
    }

    /**
     * Delete brochure file from ./uploads/courses/brochures/
     */
    private void deleteBrochureFile(String filename) {
        if (filename == null || filename.isBlank()) {
            return;
        }
        try {
            Path brochureDirPath = Paths.get(brochuresDir).toAbsolutePath();
            Path targetPath = brochureDirPath.resolve(filename);
            if (Files.exists(targetPath)) {
                Files.delete(targetPath);
                log.info("[BROCHURE] Deleted brochure file: {}", targetPath);
            }
        } catch (Exception e) {
            log.warn("[BROCHURE] Could not delete brochure file {}: {}", filename, e.getMessage());
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

        // Brochure metadata with clean CourseName_Brochure display format
        dto.setBrochurePath(course.getBrochurePath());
        if (course.getBrochurePath() != null && !course.getBrochurePath().isBlank()) {
            Path brochureDirPath = Paths.get(brochuresDir).toAbsolutePath();
            Path filePath = brochureDirPath.resolve(course.getBrochurePath());
            boolean exists = Files.exists(filePath) && filePath.toFile().length() > 0;
            dto.setHasBrochure(exists);
            String ext = getFileExtension(course.getBrochurePath());
            String displayFileName = formatBrochureFileName(course.getCourseName(), ext);
            dto.setBrochureFileName(displayFileName);
            dto.setBrochureUrl("/uploads/courses/brochures/" + course.getBrochurePath());
        } else {
            dto.setHasBrochure(false);
            dto.setBrochureFileName(formatBrochureFileName(course.getCourseName(), ".pdf"));
        }

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