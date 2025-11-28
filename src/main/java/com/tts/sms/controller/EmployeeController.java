package com.tts.sms.controller;

import com.tts.sms.dto.EmployeeRequestDTO;
import com.tts.sms.dto.EmployeeResponseDTO;
import com.tts.sms.service.EmployeeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final ObjectMapper objectMapper;

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

    // Create with multipart/form-data
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

    // Create with JSON (backward compatibility)
    @PostMapping(consumes = "application/json")
    public ResponseEntity<EmployeeResponseDTO> createEmployeeJson(
            @RequestBody EmployeeRequestDTO requestDTO) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(employeeService.createEmployee(requestDTO, null));
    }

    // Update with multipart/form-data
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

    // Update with JSON (backward compatibility)
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
}