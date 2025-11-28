package com.tts.sms.controller;

import com.tts.sms.dto.BankDTO;
import com.tts.sms.service.BankService;
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
@RequestMapping("/bank")
@RequiredArgsConstructor
public class BankController {

    private final BankService bankService;

    @GetMapping
    public String bankPage() {
        return "master/bank";
    }

    @GetMapping("/list")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getAllBanks(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        try {
            Page<BankDTO> banks = bankService.getAllBanks(search, page, size);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", banks.getContent());
            response.put("currentPage", banks.getNumber());
            response.put("totalPages", banks.getTotalPages());
            response.put("totalElements", banks.getTotalElements());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error fetching banks: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getBankById(@PathVariable Long id) {
        try {
            BankDTO bank = bankService.getBankById(id);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", bank);
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
    public ResponseEntity<Map<String, Object>> createBank(@Valid @RequestBody BankDTO bankDTO) {
        try {
            BankDTO createdBank = bankService.createBank(bankDTO);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Bank added successfully");
            response.put("data", createdBank);
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
    public ResponseEntity<Map<String, Object>> updateBank(
            @PathVariable Long id,
            @Valid @RequestBody BankDTO bankDTO) {
        try {
            BankDTO updatedBank = bankService.updateBank(id, bankDTO);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Bank updated successfully");
            response.put("data", updatedBank);
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
    public ResponseEntity<Map<String, Object>> deleteBank(@PathVariable Long id) {
        try {
            bankService.deleteBank(id);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Bank deleted successfully");
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
            bankService.exportToCSV(response, search);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            try {
                response.getWriter().write(e.getMessage());
            } catch (Exception ex) {

            }
        }
    }
}