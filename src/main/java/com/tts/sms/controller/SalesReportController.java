package com.tts.sms.controller;

import com.tts.sms.dto.CourseSalesReportDTO;
import com.tts.sms.dto.DataTablesRequest;
import com.tts.sms.dto.DataTablesResponse;
import com.tts.sms.service.SalesReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/reports/sales")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SalesReportController {

    private final SalesReportService salesReportService;

    /**
     * Get course wise sales report with DataTables server-side processing
     */
    @PostMapping(value = "/course-wise",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DataTablesResponse<CourseSalesReportDTO>> getCourseSalesReport(
            @RequestBody Map<String, Object> requestData) {

        log.info("POST /api/reports/sales/course-wise - Course: {}, Dates: {} to {}",
                requestData.get("ddlSelectedCourseId"),
                requestData.get("StartDate"),
                requestData.get("toDate"));

        try {
            // Extract parameters
            @SuppressWarnings("unchecked")
            Map<String, Object> dtParams = (Map<String, Object>) requestData.get("parameters");
            Long courseId = getLongValue(requestData.get("ddlSelectedCourseId"));
            LocalDate startDate = parseDate((String) requestData.get("StartDate"));
            LocalDate toDate = parseDate((String) requestData.get("toDate"));

            // Build DataTables request
            DataTablesRequest dtRequest = buildDataTablesRequest(dtParams);

            // Get report data
            DataTablesResponse<CourseSalesReportDTO> response = salesReportService
                    .getCourseSalesReport(courseId, startDate, toDate, dtRequest);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("Invalid request parameters: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error generating course sales report", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get all courses for dropdown
     */
    @GetMapping(value = "/courses", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Map<String, Object>>> getAllCourses() {
        log.info("GET /api/reports/sales/courses");

        try {
            List<Map<String, Object>> courses = salesReportService.getAllActiveCourses();
            return ResponseEntity.ok(courses);
        } catch (Exception e) {
            log.error("Error fetching courses", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get sales statistics for dashboard
     */
    @GetMapping(value = "/statistics", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getSalesStatistics(
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {

        log.info("GET /api/reports/sales/statistics - Course: {}, Dates: {} to {}",
                courseId, startDate, toDate);

        try {
            Map<String, Object> stats = salesReportService.getSalesStatistics(courseId, startDate, toDate);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error fetching sales statistics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Export course sales report to CSV
     */
    @GetMapping(value = "/export/csv", produces = "text/csv")
    public ResponseEntity<List<CourseSalesReportDTO>> exportToCSV(
            @RequestParam Long courseId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {

        log.info("GET /api/reports/sales/export/csv - Course: {}, Dates: {} to {}",
                courseId, startDate, toDate);

        try {
            List<CourseSalesReportDTO> data = salesReportService.exportCourseSalesReport(courseId, startDate, toDate);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            log.error("Error exporting sales report", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Helper methods
    private DataTablesRequest buildDataTablesRequest(Map<String, Object> params) {
        DataTablesRequest request = new DataTablesRequest();

        request.setDraw(getIntValue(params.get("draw")));
        request.setStart(getIntValue(params.get("start")));
        request.setLength(getIntValue(params.get("length")));

        // Search
        @SuppressWarnings("unchecked")
        Map<String, Object> search = (Map<String, Object>) params.get("search");
        if (search != null) {
            request.setSearchValue((String) search.get("value"));
        }

        // Order
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> order = (List<Map<String, Object>>) params.get("order");
        if (order != null && !order.isEmpty()) {
            Map<String, Object> firstOrder = order.get(0);
            request.setOrderColumn(getIntValue(firstOrder.get("column")));
            request.setOrderDir((String) firstOrder.get("dir"));
        }

        return request;
    }

    private Integer getIntValue(Object value) {
        if (value == null) return 0;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    private Long getLongValue(Object value) {
        if (value == null) return null;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Integer) return ((Integer) value).longValue();
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return null;
        try {
            return LocalDate.parse(dateStr);
        } catch (Exception e) {
            log.warn("Failed to parse date: {}", dateStr);
            return null;
        }
    }

    /**
     * Exception handler
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleValidationErrors(
            IllegalArgumentException ex) {

        log.error("Validation error: {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "success", "false",
                        "message", ex.getMessage()
                ));
    }
}