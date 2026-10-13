package com.tts.sms.controller;

import com.tts.sms.dto.WhatsAppAuditLogSummaryDTO;
import com.tts.sms.dto.WhatsAppKpiStatsDTO;
import com.tts.sms.model.WhatsAppAuditLog;
import com.tts.sms.service.WhatsAppAuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * REST controller for WhatsApp API Audit Logs and Billing Reconciliation.
 * Strictly accessible ONLY to users with SUPER_ADMIN role.
 */
@Slf4j
@RestController
@RequestMapping("/api/whatsapp-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class WhatsAppAuditLogController {

    private final WhatsAppAuditLogService auditLogService;

    /**
     * Get paginated WhatsApp audit logs with multi-parameter filtering.
     */
    @GetMapping
    public ResponseEntity<Page<WhatsAppAuditLogSummaryDTO>> getLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String moduleType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String triggerType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {

        log.debug("GET /api/whatsapp-logs - page: {}, size: {}, module: {}, status: {}, sortBy: {}, sortDir: {}",
                page, size, moduleType, status, sortBy, sortDir);

        String safeSortBy = sanitizeSortField(sortBy);
        Sort.Direction direction = "ASC".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, safeSortBy));

        Page<WhatsAppAuditLogSummaryDTO> result = auditLogService.getLogsWithFilters(
                startDate, endDate, moduleType, status, triggerType, search, pageable);
        return ResponseEntity.ok(result);
    }

    /**
     * Get aggregate KPI and billing reconciliation stats.
     */
    @GetMapping("/stats")
    public ResponseEntity<WhatsAppKpiStatsDTO> getStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        WhatsAppKpiStatsDTO stats = auditLogService.getKpiStats(startDate, endDate);
        return ResponseEntity.ok(stats);
    }

    /**
     * Get full details of a single audit log entry (including raw payloads).
     */
    @GetMapping("/{id}")
    public ResponseEntity<WhatsAppAuditLog> getLogDetails(@PathVariable Long id) {
        return auditLogService.getLogDetails(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Export filtered logs to CSV for Meta billing reconciliation.
     * Applies the exact same filters applied on the table and respects the active sort order.
     */
    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) String moduleType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String triggerType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {

        log.info("Exporting WhatsApp logs to CSV by SUPER_ADMIN with filters");
        String safeSortBy = sanitizeSortField(sortBy);
        Sort.Direction direction = "ASC".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(direction, safeSortBy);

        String csvContent = auditLogService.exportLogsToCsv(
                startDate, endDate, moduleType, status, triggerType, search, sort);

        String fileName = "whatsapp_api_audit_logs_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csvContent.getBytes(StandardCharsets.UTF_8));
    }

    private String sanitizeSortField(String sortBy) {
        if (sortBy == null) return "createdAt";
        return switch (sortBy.trim()) {
            case "id" -> "id";
            case "moduleType" -> "moduleType";
            case "templateName" -> "templateName";
            case "billingCategory" -> "billingCategory";
            case "triggerType" -> "triggerType";
            case "triggeredByUsername" -> "triggeredByUsername";
            case "triggeredByEmployeeName" -> "triggeredByEmployeeName";
            case "studentName" -> "studentName";
            case "recipientMobile" -> "recipientMobile";
            case "referenceNumber" -> "referenceNumber";
            case "status" -> "status";
            case "metaMessageId" -> "metaMessageId";
            default -> "createdAt";
        };
    }
}
