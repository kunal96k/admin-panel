package com.tts.sms.service;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.opencsv.CSVReader;
import com.tts.sms.dto.FeeCollectionDTO;
import com.tts.sms.dto.FeeCollectionSearchDTO;
import com.tts.sms.dto.FeeCollectionStatsDTO;
import com.tts.sms.model.FeeCollection;
import com.tts.sms.model.FeeReceipt;
import com.tts.sms.repository.AdmissionRepository;
import com.tts.sms.repository.FeeCollectionRepository;
import com.tts.sms.repository.FeeReceiptRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeeCollectionService {

    private final FeeCollectionRepository feeCollectionRepository;
    private final AdmissionRepository admissionRepository;
    private final FeeReceiptRepository feeReceiptRepository;

    private static final DateTimeFormatter[] DATE_FORMATTERS = {
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy")
    };

    /**
     * Search fee collections with filters
     */
    @Transactional(readOnly = true)
    public Page<FeeCollectionDTO> searchFeeCollections(FeeCollectionSearchDTO searchDTO) {
        log.debug("Searching fee collections: {}", searchDTO);

        String dataSource = searchDTO.getDataSource();
        if (dataSource != null && dataSource.trim().isEmpty()) {
            dataSource = null;
        }

        String paymentMode = searchDTO.getPaymentMode();
        if (paymentMode != null && paymentMode.trim().isEmpty()) {
            paymentMode = null;
        }

        final int page = Math.max(searchDTO.getPage(), 0);
        final int size = Math.max(searchDTO.getSize(), 1);
        final int offset = page * size;

        long total = feeCollectionRepository.countCombinedFeeCollections(
                searchDTO.getFromDate(),
                searchDTO.getToDate(),
                dataSource,
                paymentMode
        );

        List<FeeCollectionRepository.CombinedFeeRow> rows = feeCollectionRepository.findCombinedFeeCollections(
                searchDTO.getFromDate(),
                searchDTO.getToDate(),
                dataSource,
                paymentMode,
                size,
                offset
        );

        List<FeeCollectionDTO> pageContent = new ArrayList<>();
        for (FeeCollectionRepository.CombinedFeeRow r : rows) {
            pageContent.add(FeeCollectionDTO.builder()
                    .id(r.getId())
                    .receiptNo(r.getReceiptNo())
                    .studentName(r.getStudentName())
                    .mobileNo(r.getMobileNo())
                    .receiptDate(r.getReceiptDate())
                    .receiptDateOriginal(r.getReceiptDateOriginal())
                    .paidFees(r.getPaidFees())
                    .paymentMode(r.getPaymentMode())
                    .dataSource(r.getDataSource())
                    .registrationNumber(r.getRegistrationNumber())
                    .createdAt(r.getCreatedAt())
                    .build());
        }

        return new PageImpl<>(
                pageContent,
                PageRequest.of(page, size),
                total
        );
    }

    /**
     * Get statistics for the selected period
     */
    @Transactional(readOnly = true)
    public FeeCollectionStatsDTO getStatistics(LocalDate fromDate, LocalDate toDate,
                                               String dataSource, String paymentMode) {
        log.debug("Getting statistics for period: {} to {}", fromDate, toDate);

        // : Handle empty string as null
        if (dataSource != null && dataSource.trim().isEmpty()) {
            dataSource = null;
        }
        if (paymentMode != null && paymentMode.trim().isEmpty()) {
            paymentMode = null;
        }

        // Determine what to count based on dataSource filter
        boolean countOldData = (dataSource == null || dataSource.equals("IMPORTED_OLD_DATA"));
        boolean countNewData = (dataSource == null || dataSource.equals("NEW_ENTRY"));

        // Stats from fee_collections (old data)
        Long oldReceipts = 0L;
        Double oldAmount = 0.0;

        if (countOldData) {
            log.debug("🔍 Counting fee_collections...");
            oldReceipts = feeCollectionRepository.countByFilters(
                    fromDate, toDate, "IMPORTED_OLD_DATA", paymentMode
            );
            oldAmount = feeCollectionRepository.getTotalAmountByFilters(
                    fromDate, toDate, "IMPORTED_OLD_DATA", paymentMode
            );
            log.debug("✅ Old data: {} records, ₹{}", oldReceipts, oldAmount);
        }

        // Stats from fee_receipts (new entries)
        Long newReceipts = 0L;
        Double newAmount = 0.0;

        if (countNewData) {
            log.debug("🔍 Counting fee_receipts...");
            newReceipts = feeReceiptRepository.countByDateRangeAndPaymentMode(
                    fromDate, toDate, paymentMode
            );
            newAmount = feeReceiptRepository.getTotalReceivedByDateRangeAndPaymentMode(
                    fromDate, toDate, paymentMode
            );
            log.debug("✅ New data: {} records, ₹{}", newReceipts, newAmount);
        }

        FeeCollectionStatsDTO stats = FeeCollectionStatsDTO.builder()
                .totalReceipts((oldReceipts != null ? oldReceipts : 0L) +
                        (newReceipts != null ? newReceipts : 0L))
                .totalAmount((oldAmount != null ? oldAmount : 0.0) +
                        (newAmount != null ? newAmount : 0.0))
                .oldDataCount(oldReceipts != null ? oldReceipts : 0L)
                .newDataCount(newReceipts != null ? newReceipts : 0L)
                .build();

        log.info("📊 Statistics: Total={}, Amount=₹{}, Old={}, New={}",
                stats.getTotalReceipts(), stats.getTotalAmount(),
                stats.getOldDataCount(), stats.getNewDataCount());

        return stats;
    }

    /**
     * Get combined fee collection data (old imports + new receipts)
     * Fetches mobile numbers from Admission table
     */
    @Transactional(readOnly = true)
    public Page<FeeCollectionDTO> getCombinedFeeData(FeeCollectionSearchDTO searchDTO) {
        log.debug("Fetching combined fee data: {}", searchDTO);

        Pageable pageable = PageRequest.of(
                searchDTO.getPage(),
                searchDTO.getSize(),
                Sort.by(Sort.Direction.DESC, "receiptDate", "createdAt")
        );

        Page<FeeCollection> collections = feeCollectionRepository.findByFilters(
                searchDTO.getFromDate(),
                searchDTO.getToDate(),
                searchDTO.getDataSource(),
                searchDTO.getPaymentMode(),
                pageable
        );

        return collections.map(fc -> {
            FeeCollectionDTO dto = toDTO(fc);

            // Fetch mobile number from Admission if not present
            if ((dto.getMobileNo() == null || dto.getMobileNo().isEmpty())
                    && dto.getRegistrationNumber() != null) {
                try {
                    var admission = admissionRepository
                            .findByRegistrationNumberAndIsDeletedFalse(dto.getRegistrationNumber());
                    if (admission != null) {
                        dto.setMobileNo(admission.getMobilePrimary());
                    }
                } catch (Exception e) {
                    log.warn("Could not fetch mobile for regNo: {}", dto.getRegistrationNumber());
                }
            }

            return dto;
        });
    }

    /**
     * Import CSV data AS-IS without any validation or modification
     * Accepts ALL data including duplicates, nulls, and invalid formats
     */
    @Transactional
    public Map<String, Object> importCSVData(MultipartFile file) {
        log.info("🔄 Starting CSV import (AS-IS mode): {}", file.getOriginalFilename());

        int totalRows = 0;
        int successCount = 0;
        int errorCount = 0;
        List<String> errors = new ArrayList<>();
        String importBatchId = "BATCH_" + System.currentTimeMillis();

        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVReader csvReader = new CSVReader(reader)) {

            List<String[]> records = csvReader.readAll();

            if (records.isEmpty()) {
                throw new IllegalArgumentException("CSV file is empty");
            }

            log.debug("CSV Header: {}", Arrays.toString(records.get(0)));

            // Process ALL rows (skip only header)
            for (int i = 1; i < records.size(); i++) {
                totalRows++;
                String[] row = records.get(i);

                try {
                    FeeCollection feeCollection = FeeCollection.builder()
                            .receiptNo(getValueOrNull(row, 0))
                            .studentName(getValueOrNull(row, 1))
                            .mobileNo(getValueOrNull(row, 2))
                            .receiptDateOriginal(getValueOrNull(row, 3))
                            .receiptDate(parseDate(getValueOrNull(row, 3)))
                            .paidFees(parseDouble(getValueOrNull(row, 4)))
                            .paymentMode(getValueOrNull(row, 5))
                            .dataSource("IMPORTED_OLD_DATA")
                            .importBatchId(importBatchId)
                            .notes("Imported from CSV: " + file.getOriginalFilename())
                            .createdBy("CSV_IMPORT")
                            .build();

                    feeCollectionRepository.save(feeCollection);
                    successCount++;

                    log.debug("✅ Row {}: Imported - Receipt: {}, Student: {}",
                            i + 1, feeCollection.getReceiptNo(), feeCollection.getStudentName());

                } catch (Exception e) {
                    errorCount++;
                    String errorMsg = String.format("Row %d: %s", i + 1, e.getMessage());
                    errors.add(errorMsg);
                    log.error("❌ {}", errorMsg);
                }
            }

            log.info("✅ CSV Import Complete: {}/{} records imported (Batch: {})",
                    successCount, totalRows, importBatchId);

            return Map.of(
                    "success", true,
                    "message", String.format("Import completed: %d/%d records imported successfully",
                            successCount, totalRows),
                    "totalRecords", totalRows,
                    "successCount", successCount,
                    "errorCount", errorCount,
                    "importBatchId", importBatchId,
                    "errors", errors
            );

        } catch (Exception e) {
            log.error("❌ CSV import failed", e);
            return Map.of(
                    "success", false,
                    "message", "Import failed: " + e.getMessage(),
                    "totalRecords", 0,
                    "successCount", 0,
                    "errorCount", 0,
                    "errors", List.of(e.getMessage())
            );
        }
    }

    /**
     * Delete a fee collection record
     */
    @Transactional
    public void deleteFeeCollection(Long id) {
        log.debug("Deleting fee collection: {}", id);

        FeeCollection feeCollection = feeCollectionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Fee collection not found: " + id));

        feeCollection.setIsDeleted(true);
        feeCollection.setDeletedAt(LocalDateTime.now());
        feeCollectionRepository.save(feeCollection);

        log.info("✅ Deleted fee collection: {}", id);
    }

    // ==================== HELPER METHODS ====================

    /**
     * Get value from CSV row or return null (NO modification)
     */
    private String getValueOrNull(String[] row, int index) {
        if (index >= row.length) {
            return null;
        }
        String value = row[index];
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    /**
     * Parse date - Returns null if parsing fails (NO exception thrown)
     */
    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }

        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(dateStr.trim(), formatter);
            } catch (Exception ignored) {
                // Try next formatter
            }
        }

        log.warn("⚠️ Could not parse date: {} - Storing as null", dateStr);
        return null;
    }

    /**
     * Parse double - Returns null if parsing fails (NO exception thrown)
     */
    private Double parseDouble(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            log.warn("⚠️ Could not parse number: {} - Storing as null", value);
            return null;
        }
    }

    /**
     * Convert entity to DTO
     */
    private FeeCollectionDTO toDTO(FeeCollection entity) {
        return FeeCollectionDTO.builder()
                .id(entity.getId())
                .receiptNo(entity.getReceiptNo())
                .studentName(entity.getStudentName())
                .mobileNo(entity.getMobileNo())
                .receiptDate(entity.getReceiptDate())
                .receiptDateOriginal(entity.getReceiptDateOriginal())
                .paidFees(entity.getPaidFees())
                .paymentMode(entity.getPaymentMode())
                .dataSource(entity.getDataSource())
                .registrationNumber(entity.getRegistrationNumber())
                .importBatchId(entity.getImportBatchId())
                .notes(entity.getNotes())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public void debugDataSources(LocalDate fromDate, LocalDate toDate) {
        // Check old data
        List<FeeCollection> oldData = feeCollectionRepository.findByFiltersAsList(
                fromDate, toDate, "IMPORTED_OLD_DATA", null
        );
        log.info("🔍 DEBUG: Old data count: {}", oldData.size());

        // Check new data
        List<FeeReceipt> newData = feeReceiptRepository.findByFiltersForCollection(
                fromDate, toDate, null
        );
        log.info("🔍 DEBUG: New data count: {}", newData.size());

        // Check admissions
        long admissionCount = admissionRepository.count();
        log.info("🔍 DEBUG: Total admissions: {}", admissionCount);
    }
}