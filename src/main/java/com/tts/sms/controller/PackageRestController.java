package com.tts.sms.controller;

import com.tts.sms.dto.*;
import com.tts.sms.service.CourseService;
import com.tts.sms.service.PackageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/packages")
@RequiredArgsConstructor
@Slf4j
public class PackageRestController {

    private final PackageService packageService;
    private final CourseService courseService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllPackages(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            Page<PackageDTO> packagePage = packageService.getPackagesPaginated(page, size);

            Map<String, Object> response = new HashMap<>();
            response.put("packages", packagePage.getContent());
            response.put("currentPage", packagePage.getNumber());
            response.put("totalItems", packagePage.getTotalElements());
            response.put("totalPages", packagePage.getTotalPages());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching packages", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchPackages(
            @RequestParam String searchTerm,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            Page<PackageDTO> packagePage = packageService.searchPackages(searchTerm, page, size);

            Map<String, Object> response = new HashMap<>();
            response.put("packages", packagePage.getContent());
            response.put("currentPage", packagePage.getNumber());
            response.put("totalItems", packagePage.getTotalElements());
            response.put("totalPages", packagePage.getTotalPages());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error searching packages", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getPackageById(@PathVariable Long id) {
        try {
            PackageDTO packageDTO = packageService.getPackageById(id);
            return ResponseEntity.ok(packageDTO);
        } catch (RuntimeException e) {
            log.error("Package not found: {}", id, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error fetching package", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> createPackage(@RequestBody PackageCreateRequest request) {
        try {
            PackageDTO packageDTO = packageService.createPackage(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(packageDTO);
        } catch (RuntimeException e) {
            log.error("Error creating package", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error creating package", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updatePackage(
            @PathVariable Long id,
            @RequestBody PackageUpdateRequest request) {
        try {
            PackageDTO packageDTO = packageService.updatePackage(id, request);
            return ResponseEntity.ok(packageDTO);
        } catch (RuntimeException e) {
            log.error("Error updating package", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating package", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePackage(@PathVariable Long id) {
        try {
            packageService.deletePackage(id);
            return ResponseEntity.ok(Map.of("message", "Package deleted successfully"));
        } catch (RuntimeException e) {
            log.error("Error deleting package", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error deleting package", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/export/csv")
    public ResponseEntity<?> exportPackagesToCSV() {
        try {
            List<PackageCSVExportDTO> packages = packageService.exportPackagesToCSV();

            if (packages.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                        "message", "No packages available to export. The table is empty."
                ));
            }

            return ResponseEntity.ok(packages);
        } catch (Exception e) {
            log.error("Error exporting packages", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/courses/active")
    public ResponseEntity<?> getActiveCourses() {
        try {
            List<CourseDTO> courses = courseService.getAllCourses();
            return ResponseEntity.ok(courses);
        } catch (Exception e) {
            log.error("Error fetching active courses", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}