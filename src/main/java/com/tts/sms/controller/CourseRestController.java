package com.tts.sms.controller;

import com.tts.sms.dto.CourseDTO;
import com.tts.sms.dto.CourseCSVExportDTO;
import com.tts.sms.service.CourseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API Controller for Course Management
 */
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
@Slf4j
public class CourseRestController {

    private final CourseService courseService;

    /**
     * Get all courses with pagination
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllCourses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        try {
            Page<CourseDTO> coursePage = courseService.getCoursesPaginated(page, size);

            Map<String, Object> response = new HashMap<>();
            response.put("courses", coursePage.getContent());
            response.put("currentPage", coursePage.getNumber());
            response.put("totalItems", coursePage.getTotalElements());
            response.put("totalPages", coursePage.getTotalPages());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching courses", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Search courses
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchCourses(
            @RequestParam String searchTerm,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        try {
            Page<CourseDTO> coursePage = courseService.searchCourses(searchTerm, page, size);

            Map<String, Object> response = new HashMap<>();
            response.put("courses", coursePage.getContent());
            response.put("currentPage", coursePage.getNumber());
            response.put("totalItems", coursePage.getTotalElements());
            response.put("totalPages", coursePage.getTotalPages());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error searching courses", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get course by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getCourseById(@PathVariable Long id) {
        try {
            CourseDTO course = courseService.getCourseById(id);
            return ResponseEntity.ok(course);
        } catch (RuntimeException e) {
            log.error("Course not found: {}", id, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error fetching course", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Create new course
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createCourse(
            @RequestParam String courseName,
            @RequestParam BigDecimal courseFees,
            @RequestParam(required = false) MultipartFile courseImage) {
        try {
            CourseDTO course = courseService.createCourse(courseName, courseFees, courseImage);
            return ResponseEntity.status(HttpStatus.CREATED).body(course);
        } catch (RuntimeException e) {
            log.error("Error creating course", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error creating course", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Update course
     */
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateCourse(
            @PathVariable Long id,
            @RequestParam String courseName,
            @RequestParam BigDecimal courseFees,
            @RequestParam(required = false) MultipartFile courseImage) {
        try {
            CourseDTO course = courseService.updateCourse(id, courseName, courseFees, courseImage);
            return ResponseEntity.ok(course);
        } catch (RuntimeException e) {
            log.error("Error updating course", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating course", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Delete course
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCourse(@PathVariable Long id) {
        try {
            courseService.deleteCourse(id);
            return ResponseEntity.ok(Map.of("message", "Course deleted successfully"));
        } catch (RuntimeException e) {
            log.error("Error deleting course", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error deleting course", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Export courses to CSV
     */
    @GetMapping("/export/csv")
    public ResponseEntity<List<CourseCSVExportDTO>> exportCoursesToCSV() {
        try {
            List<CourseCSVExportDTO> courses = courseService.exportCoursesToCSV();
            return ResponseEntity.ok(courses);
        } catch (Exception e) {
            log.error("Error exporting courses", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}