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
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.opencsv.CSVReader;
import com.tts.sms.dto.FeeCollectionDTO;
import com.tts.sms.dto.FeeCollectionSearchDTO;
import com.tts.sms.dto.FeeCollectionStatsDTO;
import com.tts.sms.model.Admission;
import com.tts.sms.model.FeeCollection;
import com.tts.sms.model.CombinedFeeCollection;
import com.tts.sms.model.FeeReceipt;
import com.tts.sms.model.Fees;
import com.tts.sms.repository.AdmissionRepository;
import com.tts.sms.repository.FeeCollectionRepository;
import com.tts.sms.repository.CombinedFeeCollectionRepository;
import com.tts.sms.repository.FeeReceiptRepository;
import com.tts.sms.repository.FeesRepository;
import com.tts.sms.specification.CombinedFeeCollectionSpecifications;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeeCollectionService {

    private final FeeCollectionRepository feeCollectionRepository;
    private final CombinedFeeCollectionRepository combinedFeeCollectionRepository;
    private final AdmissionRepository admissionRepository;
    private final FeeReceiptRepository feeReceiptRepository;
    private final FeesRepository feesRepository;
    private final FeesManagerService feesManagerService;
    private final EntityManager entityManager;

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

        Specification<CombinedFeeCollection> spec = CombinedFeeCollectionSpecifications.getSearchSpecification(
                searchDTO.getFromDate(),
                searchDTO.getToDate(),
                searchDTO.getDataSource(),
                searchDTO.getPaymentMode(),
                searchDTO.getSearchType(),
                searchDTO.getSearchQuery()
        );

        final int page = Math.max(searchDTO.getPage(), 0);
        final int size = Math.max(searchDTO.getSize(), 1);

        // Sort by receiptDate DESC, createdAt DESC
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "receiptDate", "createdAt")
        );

        Page<CombinedFeeCollection> results = combinedFeeCollectionRepository.findAll(spec, pageable);

        return results.map(r -> {
            String mob = r.getMobileNo();
            String studentName = r.getStudentName();

            if ((mob == null || mob.trim().isEmpty() || "N/A".equalsIgnoreCase(mob))
                    && r.getRegistrationNumber() != null) {
                try {
                    var admission = admissionRepository
                            .findByRegistrationNumberAndIsDeletedFalse(r.getRegistrationNumber());
                    if (admission != null) {
                        if (admission.getMobilePrimary() != null && !admission.getMobilePrimary().isEmpty()) {
                            mob = admission.getMobilePrimary();
                        } else if (admission.getMobileSecondary() != null) {
                            mob = admission.getMobileSecondary();
                        }
                        if (studentName == null || studentName.trim().isEmpty() || "N/A".equalsIgnoreCase(studentName)) {
                            String fullName = String.join(" ",
                                    admission.getFirstName() != null ? admission.getFirstName() : "",
                                    admission.getMiddleName() != null ? admission.getMiddleName() : "",
                                    admission.getLastName() != null ? admission.getLastName() : ""
                            ).trim();
                            if (!fullName.isEmpty()) {
                                studentName = fullName;
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Could not fetch admission fallback details for regNo: {}", r.getRegistrationNumber());
                }
            }

            return FeeCollectionDTO.builder()
                    .id(r.getOriginalId()) // Return original table ID for action buttons to work
                    .receiptNo(r.getReceiptNo())
                    .studentName(studentName)
                    .mobileNo(mob)
                    .receiptDate(r.getReceiptDate())
                    .receiptDateOriginal(r.getReceiptDateOriginal())
                    .paidFees(r.getPaidFees())
                    .paymentMode(r.getPaymentMode())
                    .dataSource(r.getDataSource())
                    .registrationNumber(r.getRegistrationNumber())
                    .createdAt(r.getCreatedAt())
                    .build();
        });
    }

    /**
     * Get statistics for the selected period
     */
    @Transactional(readOnly = true)
    public FeeCollectionStatsDTO getStatistics(LocalDate fromDate, LocalDate toDate,
                                               String dataSource, String paymentMode,
                                               String searchType, String searchQuery) {
        log.debug("Getting statistics for period: {} to {}", fromDate, toDate);

        Specification<CombinedFeeCollection> baseSpec = CombinedFeeCollectionSpecifications.getSearchSpecification(
                fromDate, toDate, dataSource, paymentMode, searchType, searchQuery
        );

        long totalReceipts = combinedFeeCollectionRepository.count(baseSpec);

        // Sum total amount using CriteriaBuilder
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Double> sumQuery = cb.createQuery(Double.class);
        Root<CombinedFeeCollection> root = sumQuery.from(CombinedFeeCollection.class);
        sumQuery.select(cb.sum(root.get("paidFees")));
        if (baseSpec != null) {
            Predicate predicate = baseSpec.toPredicate(root, sumQuery, cb);
            if (predicate != null) {
                sumQuery.where(predicate);
            }
        }
        Double totalAmount = entityManager.createQuery(sumQuery).getSingleResult();
        if (totalAmount == null) {
            totalAmount = 0.0;
        }

        // Count old data
        Specification<CombinedFeeCollection> oldSpec = (root1, query, cb1) -> {
            Predicate p = baseSpec.toPredicate(root1, query, cb1);
            Predicate sourceP = cb1.equal(root1.get("dataSource"), "IMPORTED_OLD_DATA");
            return p != null ? cb1.and(p, sourceP) : sourceP;
        };
        long oldDataCount = combinedFeeCollectionRepository.count(oldSpec);

        // Count new data
        Specification<CombinedFeeCollection> newSpec = (root2, query, cb2) -> {
            Predicate p = baseSpec.toPredicate(root2, query, cb2);
            Predicate sourceP = cb2.equal(root2.get("dataSource"), "NEW_ENTRY");
            return p != null ? cb2.and(p, sourceP) : sourceP;
        };
        long newDataCount = combinedFeeCollectionRepository.count(newSpec);

        FeeCollectionStatsDTO stats = FeeCollectionStatsDTO.builder()
                .totalReceipts(totalReceipts)
                .totalAmount(totalAmount)
                .oldDataCount(oldDataCount)
                .newDataCount(newDataCount)
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

        // Recalculate fees for the student associated with this mobile number
        String mobile = feeCollection.getMobileNo();
        if (mobile != null && !mobile.trim().isEmpty() && !"N/A".equalsIgnoreCase(mobile)) {
            // Find admission records
            List<Admission> admissions = admissionRepository.findByMobilePrimaryAndIsDeletedFalse(mobile);
            if (!admissions.isEmpty()) {
                for (Admission admission : admissions) {
                    String regNo = admission.getRegistrationNumber();
                    if (regNo != null && !regNo.trim().isEmpty()) {
                        log.info("Recalculating fees for regNo: {} after deleting fee collection: {}", regNo, id);
                        feesManagerService.recalculateFeesFromTransactions(regNo);
                    }
                }
            } else {
                // If not found in admissions, check fees table directly
                List<Fees> feesList = feesRepository.findByMobileAndIsDeletedFalse(mobile);
                for (Fees fees : feesList) {
                    String regNo = fees.getRegistrationNumber();
                    if (regNo != null && !regNo.trim().isEmpty()) {
                        log.info("Recalculating fees for regNo: {} from Fees table after deleting fee collection: {}", regNo, id);
                        feesManagerService.recalculateFeesFromTransactions(regNo);
                    }
                }
            }
        }
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