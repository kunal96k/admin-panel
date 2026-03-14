package com.tts.sms.controller;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.tts.sms.dto.FeeCollectionDTO;
import com.tts.sms.dto.FeeCollectionSearchDTO;
import com.tts.sms.dto.FeeCollectionStatsDTO;
import com.tts.sms.service.FeeCollectionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/fee-collections")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class FeeCollectionController {

    private final FeeCollectionService feeCollectionService;

    /**
     * Search fee collections with filters
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Page<FeeCollectionDTO>> searchFeeCollections(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String paymentMode,
            @RequestParam(required = false) String dataSource,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        log.info("GET /api/fee-collections - fromDate: {}, toDate: {}, paymentMode: {}, dataSource: {}",
                fromDate, toDate, paymentMode, dataSource);

        FeeCollectionSearchDTO searchDTO = FeeCollectionSearchDTO.builder()
                .fromDate(fromDate)
                .toDate(toDate)
                .paymentMode(paymentMode)
                .dataSource(dataSource)
                .page(page)
                .size(size)
                .build();

        Page<FeeCollectionDTO> results = feeCollectionService.searchFeeCollections(searchDTO);
        return ResponseEntity.ok(results);
    }

    /**
     * Get statistics for the selected period
     */
    @GetMapping(value = "/statistics", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FeeCollectionStatsDTO> getStatistics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String paymentMode,
            @RequestParam(required = false) String dataSource) {

        log.info("GET /api/fee-collections/statistics - fromDate: {}, toDate: {}",
                fromDate, toDate);

        FeeCollectionStatsDTO stats = feeCollectionService.getStatistics(
                fromDate, toDate, dataSource, paymentMode
        );
        return ResponseEntity.ok(stats);
    }

    /**
     * Import CSV data (accepts ALL data AS-IS without validation)
     */
    @PostMapping(value = "/import-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> importCSV(
            @RequestParam("file") MultipartFile file) {

        log.info("POST /api/fee-collections/import-csv - file: {}", file.getOriginalFilename());

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "success", false,
                            "message", "File is empty"
                    ));
        }

        if (!file.getOriginalFilename().toLowerCase().endsWith(".csv")) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "success", false,
                            "message", "Only CSV files are allowed"
                    ));
        }

        Map<String, Object> result = feeCollectionService.importCSVData(file);

        if ((boolean) result.get("success")) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    /**
     * Delete a fee collection record
     */
    @DeleteMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> deleteFeeCollection(@PathVariable Long id) {
        log.info("DELETE /api/fee-collections/{}", id);

        try {
            feeCollectionService.deleteFeeCollection(id);
            return ResponseEntity.ok(Map.of(
                    "success", "true",
                    "message", "Fee collection deleted successfully"
            ));
        } catch (Exception e) {
            log.error("Error deleting fee collection", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", "false",
                            "message", e.getMessage()
                    ));
        }
    }

    /**
     * Exception handler
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleException(Exception ex) {
        log.error("Error in fee collection controller", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "success", "false",
                        "message", ex.getMessage()
                ));
    }
}