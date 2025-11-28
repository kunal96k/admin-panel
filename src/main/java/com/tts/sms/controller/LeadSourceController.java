package com.tts.sms.controller;

import com.tts.sms.dto.LeadSourceDTO;
import com.tts.sms.service.LeadSourceService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/lead-source")
@RequiredArgsConstructor
public class LeadSourceController {

    private final LeadSourceService leadSourceService;

    @GetMapping
    public String leadSourcePage() {
        return "lead-source/lead-source";
    }

    @GetMapping("/list")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getAllLeadSources(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        try {
            Page<LeadSourceDTO> leadSources = leadSourceService.getAllLeadSources(search, page, size);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", leadSources.getContent());
            response.put("currentPage", leadSources.getNumber());
            response.put("totalPages", leadSources.getTotalPages());
            response.put("totalElements", leadSources.getTotalElements());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error fetching lead sources: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getLeadSourceById(@PathVariable Long id) {
        try {
            LeadSourceDTO leadSource = leadSourceService.getLeadSourceById(id);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", leadSource);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        }
    }

    @PostMapping("/create")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createLeadSource(@Valid @RequestBody LeadSourceDTO leadSourceDTO) {
        try {
            LeadSourceDTO createdLeadSource = leadSourceService.createLeadSource(leadSourceDTO);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Lead source added successfully");
            response.put("data", createdLeadSource);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
    }

    @PutMapping("/update/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateLeadSource(
            @PathVariable Long id,
            @Valid @RequestBody LeadSourceDTO leadSourceDTO) {
        try {
            LeadSourceDTO updatedLeadSource = leadSourceService.updateLeadSource(id, leadSourceDTO);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Lead source updated successfully");
            response.put("data", updatedLeadSource);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
    }

    @DeleteMapping("/delete/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteLeadSource(@PathVariable Long id) {
        try {
            leadSourceService.deleteLeadSource(id);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Lead source deleted successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
    }

    @GetMapping("/export")
    public void exportCSV(
            @RequestParam(defaultValue = "") String search,
            HttpServletResponse response) {
        try {
            leadSourceService.exportToCSV(response, search);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            try {
                response.getWriter().write(e.getMessage());
            } catch (Exception ex) {
                // Log error
            }
        }
    }
}