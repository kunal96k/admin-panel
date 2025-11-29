package com.tts.sms.controller;

import com.tts.sms.dto.*;
import com.tts.sms.exception.BadRequestException;
import com.tts.sms.exception.ResourceNotFoundException;
import com.tts.sms.service.BatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/batches")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
public class BatchController {

    private final BatchService batchService;

    /**
     * Get all batches with pagination
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllBatches(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        try {
            BatchPageResponseDTO response = batchService.getAllBatches(page, size);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("batches", response.getBatches());
            result.put("totalElements", response.getTotalElements());
            result.put("totalPages", response.getTotalPages());
            result.put("currentPage", response.getCurrentPage());
            result.put("pageSize", response.getPageSize());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error fetching batches: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to fetch batches: " + e.getMessage()));
        }
    }

    /**
     * Search batches with filters
     */
    @PostMapping("/search")
    public ResponseEntity<Map<String, Object>> searchBatches(@RequestBody BatchSearchDTO searchDTO) {
        try {
            BatchPageResponseDTO response = batchService.searchBatches(searchDTO);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("batches", response.getBatches());
            result.put("totalElements", response.getTotalElements());
            result.put("totalPages", response.getTotalPages());
            result.put("currentPage", response.getCurrentPage());
            result.put("pageSize", response.getPageSize());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error searching batches: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to search batches: " + e.getMessage()));
        }
    }

    /**
     * Get batch by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getBatchById(@PathVariable Long id) {
        try {
            BatchResponseDTO batch = batchService.getBatchById(id);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("batch", batch);

            return ResponseEntity.ok(result);
        } catch (ResourceNotFoundException e) {
            log.error("Batch not found: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(createErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("Error fetching batch: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to fetch batch: " + e.getMessage()));
        }
    }

    /**
     * Create new batch
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createBatch(
            @Valid @RequestBody BatchRequestDTO requestDTO,
            BindingResult bindingResult) {
        try {
            // Validate input
            if (bindingResult.hasErrors()) {
                String errors = bindingResult.getFieldErrors().stream()
                        .map(error -> error.getField() + ": " + error.getDefaultMessage())
                        .collect(Collectors.joining(", "));
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(createErrorResponse("Validation failed: " + errors));
            }

            String username = getCurrentUsername();
            BatchResponseDTO batch = batchService.createBatch(requestDTO, username);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Batch created successfully");
            result.put("batch", batch);

            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (BadRequestException e) {
            log.error("Validation error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(createErrorResponse(e.getMessage()));
        } catch (ResourceNotFoundException e) {
            log.error("Resource not found: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(createErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("Error creating batch: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to create batch: " + e.getMessage()));
        }
    }

    /**
     * Update existing batch
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateBatch(
            @PathVariable Long id,
            @Valid @RequestBody BatchRequestDTO requestDTO,
            BindingResult bindingResult) {
        try {
            // Validate input
            if (bindingResult.hasErrors()) {
                String errors = bindingResult.getFieldErrors().stream()
                        .map(error -> error.getField() + ": " + error.getDefaultMessage())
                        .collect(Collectors.joining(", "));
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(createErrorResponse("Validation failed: " + errors));
            }

            String username = getCurrentUsername();
            BatchResponseDTO batch = batchService.updateBatch(id, requestDTO, username);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Batch updated successfully");
            result.put("batch", batch);

            return ResponseEntity.ok(result);
        } catch (BadRequestException e) {
            log.error("Validation error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(createErrorResponse(e.getMessage()));
        } catch (ResourceNotFoundException e) {
            log.error("Batch not found: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(createErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating batch: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to update batch: " + e.getMessage()));
        }
    }

    /**
     * Delete batch (Hard delete)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteBatch(@PathVariable Long id) {
        try {
            String username = getCurrentUsername();
            batchService.deleteBatch(id, username);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Batch deleted successfully");

            return ResponseEntity.ok(result);
        } catch (ResourceNotFoundException e) {
            log.error("Batch not found: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(createErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("Error deleting batch: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to delete batch: " + e.getMessage()));
        }
    }

    /**
     * Attach course to batch
     */
    @PostMapping("/{batchId}/attach-course/{courseId}")
    public ResponseEntity<Map<String, Object>> attachCourse(
            @PathVariable Long batchId,
            @PathVariable Long courseId) {
        try {
            String username = getCurrentUsername();
            BatchResponseDTO batch = batchService.attachCourse(batchId, courseId, username);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Course attached successfully");
            result.put("batch", batch);

            return ResponseEntity.ok(result);
        } catch (ResourceNotFoundException e) {
            log.error("Resource not found: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(createErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("Error attaching course: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to attach course: " + e.getMessage()));
        }
    }

    /**
     * Get current authenticated username
     */
    private String getCurrentUsername() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                return authentication.getName();
            }
        } catch (Exception e) {
            log.warn("Unable to get authenticated user: {}", e.getMessage());
        }
        return "system";
    }

    /**
     * Create error response
     */
    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("message", message);
        return error;
    }
}