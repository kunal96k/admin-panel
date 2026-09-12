package com.tts.sms.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tts.sms.dto.EmployeeRequestDTO;
import com.tts.sms.dto.EmployeeResponseDTO;
import com.tts.sms.service.EmployeeService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final ObjectMapper objectMapper;

    /**
     * Get user credentials for an employee
     */
    @GetMapping("/{id}/user-credentials")
    public ResponseEntity<Map<String, String>> getUserCredentials(@PathVariable Long id) {
        Map<String, String> credentials = employeeService.getUserCredentials(id);
        return ResponseEntity.ok(credentials);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllEmployees(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(employeeService.getAllEmployees(page, size));
    }

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchEmployees(
            @RequestParam String searchTerm,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(employeeService.searchEmployees(searchTerm, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmployeeResponseDTO> getEmployeeById(@PathVariable Long id) {
        return ResponseEntity.ok(employeeService.getEmployeeById(id));
    }

    /**
     * NEW: Get current logged-in user's employee profile
     */
    @GetMapping("/current-user")
    public ResponseEntity<EmployeeResponseDTO> getCurrentUserProfile() {
        return ResponseEntity.ok(employeeService.getCurrentUserProfile());
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<EmployeeResponseDTO> createEmployeeMultipart(
            @RequestParam("data") String employeeData,
            @RequestParam(value = "photo", required = false) MultipartFile photoFile) {
        try {
            EmployeeRequestDTO requestDTO = objectMapper.readValue(employeeData, EmployeeRequestDTO.class);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(employeeService.createEmployee(requestDTO, photoFile));
        } catch (Exception e) {
            throw new RuntimeException("Failed to create employee: " + e.getMessage());
        }
    }

    @PostMapping(consumes = "application/json")
    public ResponseEntity<EmployeeResponseDTO> createEmployeeJson(
            @RequestBody EmployeeRequestDTO requestDTO) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(employeeService.createEmployee(requestDTO, null));
    }

    @PutMapping(value = "/{id}", consumes = "multipart/form-data")
    public ResponseEntity<EmployeeResponseDTO> updateEmployeeMultipart(
            @PathVariable Long id,
            @RequestParam("data") String employeeData,
            @RequestParam(value = "photo", required = false) MultipartFile photoFile) {
        try {
            EmployeeRequestDTO requestDTO = objectMapper.readValue(employeeData, EmployeeRequestDTO.class);
            return ResponseEntity.ok(employeeService.updateEmployee(id, requestDTO, photoFile));
        } catch (Exception e) {
            throw new RuntimeException("Failed to update employee: " + e.getMessage());
        }
    }

    @PutMapping(value = "/{id}", consumes = "application/json")
    public ResponseEntity<EmployeeResponseDTO> updateEmployeeJson(
            @PathVariable Long id,
            @RequestBody EmployeeRequestDTO requestDTO) {
        return ResponseEntity.ok(employeeService.updateEmployee(id, requestDTO, null));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmployee(@PathVariable Long id) {
        employeeService.deleteEmployee(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/send-credentials")
    public ResponseEntity<Map<String, String>> sendCredentials(@PathVariable Long id) {
        employeeService.sendCredentialsEmail(id);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Credentials sent successfully");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/unlock")
    public ResponseEntity<Map<String, String>> unlockAndActivateUser(@PathVariable Long id) {
        employeeService.unlockAndActivateUser(id);
        Map<String, String> response = new HashMap<>();
        response.put("message", "User unlocked and activated successfully");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/lock")
    public ResponseEntity<Map<String, String>> lockUser(@PathVariable Long id) {
        employeeService.lockUser(id);
        Map<String, String> response = new HashMap<>();
        response.put("message", "User locked successfully");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/unlock-only")
    public ResponseEntity<Map<String, String>> unlockUser(@PathVariable Long id) {
        employeeService.unlockUser(id);
        Map<String, String> response = new HashMap<>();
        response.put("message", "User unlocked successfully");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<Map<String, String>> deactivateUser(@PathVariable Long id) {
        employeeService.deactivateUser(id);
        Map<String, String> response = new HashMap<>();
        response.put("message", "User deactivated successfully");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<Map<String, String>> activateUser(@PathVariable Long id) {
        employeeService.activateUser(id);
        Map<String, String> response = new HashMap<>();
        response.put("message", "User activated successfully");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/can-modify")
    public ResponseEntity<Map<String, Object>> canModifyEmployee(@PathVariable Long id) {
        try {
            Map<String, Object> result = employeeService.canModifyEmployee(id);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("canModify", false);
            errorResponse.put("canDelete", false);
            errorResponse.put("message", "Security check failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
        }
    }

    @GetMapping("/{id}/permissions")
    public ResponseEntity<List<Map<String, Object>>> getEmployeePermissions(@PathVariable Long id) {
        return ResponseEntity.ok(employeeService.getEmployeePermissions(id));
    }
}