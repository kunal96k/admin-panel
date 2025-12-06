package com.tts.sms.controller;

import com.tts.sms.dto.*;
import com.tts.sms.service.AdmissionEnhancedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class AdmissionDropdownController {

    private final AdmissionEnhancedService admissionEnhancedService;

    @GetMapping("/packages/dropdown")
    public ResponseEntity<List<PackageWithCoursesDTO>> getPackagesDropdown() {
        List<PackageWithCoursesDTO> packages = admissionEnhancedService.getActivePackagesWithCourses();
        return ResponseEntity.ok(packages);
    }

    @GetMapping("/courses/dropdown")
    public ResponseEntity<List<CourseDropdownDTO>> getCoursesDropdown() {
        List<CourseDropdownDTO> courses = admissionEnhancedService.getActiveCoursesForDropdown();
        return ResponseEntity.ok(courses);
    }

    @GetMapping("/subjects/course/{courseId}")
    public ResponseEntity<List<SubjectDropdownDTO>> getSubjectsByCourse(@PathVariable Long courseId) {
        List<SubjectDropdownDTO> subjects = admissionEnhancedService.getSubjectsByCourseId(courseId);
        return ResponseEntity.ok(subjects);
    }

    @GetMapping("/batches/dropdown")
    public ResponseEntity<List<BatchDropdownDTO>> getBatchesDropdown() {
        List<BatchDropdownDTO> batches = admissionEnhancedService.getActiveBatches();
        return ResponseEntity.ok(batches);
    }

    @GetMapping("/lead-sources/dropdown")
    public ResponseEntity<List<LeadSourceDropdownDTO>> getLeadSourcesDropdown() {
        List<LeadSourceDropdownDTO> sources = admissionEnhancedService.getActiveLeadSources();
        return ResponseEntity.ok(sources);
    }
}