package com.tts.sms.controller;

import com.tts.sms.dto.SubjectDTO;
import com.tts.sms.service.SubjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API Controller for Subject Management
 */
@RestController
@RequestMapping("/api/subjects")
@RequiredArgsConstructor
@Slf4j
public class SubjectRestController {

    private final SubjectService subjectService;

    /**
     * Get all subjects by course ID
     */
    @GetMapping("/course/{courseId}")
    public ResponseEntity<?> getSubjectsByCourseId(@PathVariable Long courseId) {
        try {
            List<SubjectDTO> subjects = subjectService.getSubjectsByCourseId(courseId);
            return ResponseEntity.ok(subjects);
        } catch (Exception e) {
            log.error("Error fetching subjects for course: {}", courseId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get subject by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getSubjectById(@PathVariable Long id) {
        try {
            SubjectDTO subject = subjectService.getSubjectById(id);
            return ResponseEntity.ok(subject);
        } catch (RuntimeException e) {
            log.error("Subject not found: {}", id, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error fetching subject", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Create new subject
     */
    @PostMapping
    public ResponseEntity<?> createSubject(
            @RequestParam String subjectName,
            @RequestParam Long courseId) {
        try {
            SubjectDTO subject = subjectService.createSubject(subjectName, courseId);
            return ResponseEntity.status(HttpStatus.CREATED).body(subject);
        } catch (RuntimeException e) {
            log.error("Error creating subject", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error creating subject", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Update subject
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateSubject(
            @PathVariable Long id,
            @RequestParam String subjectName,
            @RequestParam Long courseId) {
        try {
            SubjectDTO subject = subjectService.updateSubject(id, subjectName, courseId);
            return ResponseEntity.ok(subject);
        } catch (RuntimeException e) {
            log.error("Error updating subject", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating subject", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Delete subject
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteSubject(@PathVariable Long id) {
        try {
            subjectService.deleteSubject(id);
            return ResponseEntity.ok(Map.of("message", "Subject deleted successfully"));
        } catch (RuntimeException e) {
            log.error("Error deleting subject", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error deleting subject", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Count subjects by course
     */
    @GetMapping("/course/{courseId}/count")
    public ResponseEntity<?> countSubjectsByCourse(@PathVariable Long courseId) {
        try {
            long count = subjectService.countSubjectsByCourse(courseId);
            return ResponseEntity.ok(Map.of("count", count));
        } catch (Exception e) {
            log.error("Error counting subjects", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}