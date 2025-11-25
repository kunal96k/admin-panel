package com.tts.sms.service;

import com.tts.sms.dto.*;
import com.tts.sms.model.Enquiry;
import com.tts.sms.exception.ResourceNotFoundException;
import com.tts.sms.repository.EnquiryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnquiryService {

    private final EnquiryRepository enquiryRepository;
    private final EnquiryMapper enquiryMapper;
    private final CSVService csvService;

    @PersistenceContext
    private EntityManager entityManager;

    // ==================== EXISTING METHODS (UNCHANGED) ====================

    @Transactional(readOnly = true)
    public Page<EnquiryResponseDTO> getAllEnquiries(int page, int size) {
        log.debug("Fetching enquiries - page: {}, size: {}", page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by("enquiryDate").descending());
        Page<Enquiry> enquiries = enquiryRepository.findByIsDeletedFalse(pageable);
        log.info("Retrieved {} enquiries out of {} total",
                enquiries.getNumberOfElements(), enquiries.getTotalElements());
        return enquiries.map(enquiryMapper::toResponseDTO);
    }

    @Transactional(readOnly = true)
    public Page<EnquiryResponseDTO> searchEnquiries(EnquirySearchDTO searchDTO) {
        log.debug("Searching enquiries with criteria: {}", searchDTO);

        // ✅ Map camelCase to snake_case for database columns
        String sortColumn = "enquiry_date"; // Default

        if ("enquiryDate".equals(searchDTO.getSortBy())) {
            sortColumn = "enquiry_date";
        } else if ("firstName".equals(searchDTO.getSortBy())) {
            sortColumn = "first_name";
        } else if ("lastName".equals(searchDTO.getSortBy())) {
            sortColumn = "last_name";
        } else if ("mobile".equals(searchDTO.getSortBy())) {
            sortColumn = "mobile";
        }

        Sort sort = Sort.by(
                "DESC".equalsIgnoreCase(searchDTO.getSortDirection())
                        ? Sort.Direction.DESC
                        : Sort.Direction.ASC,
                sortColumn
        );

        Pageable pageable = PageRequest.of(searchDTO.getPage(), searchDTO.getSize(), sort);

        Page<Enquiry> results = enquiryRepository.advancedSearch(
                searchDTO.getSearchTerm(),
                searchDTO.getStatus(),
                searchDTO.getSource(),
                searchDTO.getCourse(),
                searchDTO.getAssignTo(),
                pageable
        );
        log.info("Search returned {} results", results.getTotalElements());
        return results.map(enquiryMapper::toResponseDTO);
    }

    @Transactional(readOnly = true)
    public EnquiryResponseDTO getEnquiryById(Long id) {
        log.debug("Fetching enquiry with id: {}", id);
        Enquiry enquiry = enquiryRepository.findById(id)
                .filter(e -> !e.getIsDeleted())
                .orElseThrow(() -> {
                    log.error("Enquiry not found with id: {}", id);
                    return new ResourceNotFoundException("Enquiry not found with id: " + id);
                });
        log.info("Retrieved enquiry: {}", enquiry.getDisplayName());
        return enquiryMapper.toResponseDTO(enquiry);
    }

    @Transactional
    public EnquiryResponseDTO createEnquiry(EnquiryRequestDTO requestDTO) {
        log.debug("Creating new enquiry for mobile: {}", requestDTO.getMobile());

        Enquiry enquiry = enquiryMapper.toEntity(requestDTO);
        enquiry.setImportSource("MANUAL");
        enquiry.setCreatedBy("SYSTEM");
        Enquiry saved = enquiryRepository.save(enquiry);
        log.info("Created enquiry with id: {} for {}", saved.getId(), saved.getDisplayName());
        return enquiryMapper.toResponseDTO(saved);
    }

    @Transactional
    public EnquiryResponseDTO updateEnquiry(Long id, EnquiryRequestDTO requestDTO) {
        log.debug("Updating enquiry with id: {}", id);
        Enquiry existingEnquiry = enquiryRepository.findById(id)
                .filter(e -> !e.getIsDeleted())
                .orElseThrow(() -> {
                    log.error("Enquiry not found with id: {}", id);
                    return new ResourceNotFoundException("Enquiry not found with id: " + id);
                });
        if (!existingEnquiry.getMobile().equals(requestDTO.getMobile()) &&
                enquiryRepository.existsByMobileAndIsDeletedFalse(requestDTO.getMobile())) {
            log.warn("Mobile number {} already exists for another enquiry", requestDTO.getMobile());
            throw new IllegalArgumentException(
                    "Mobile number " + requestDTO.getMobile() + " already exists"
            );
        }
        enquiryMapper.updateEntityFromDTO(requestDTO, existingEnquiry);
        existingEnquiry.setUpdatedBy("SYSTEM");
        Enquiry updated = enquiryRepository.save(existingEnquiry);
        log.info("Updated enquiry with id: {}", id);
        return enquiryMapper.toResponseDTO(updated);
    }

    @Transactional
    public EnquiryResponseDTO updateEnquiryStatus(Long id, String status) {
        log.debug("Updating status for enquiry with id: {} to {}", id, status);
        Enquiry enquiry = enquiryRepository.findById(id)
                .filter(e -> !e.getIsDeleted())
                .orElseThrow(() -> {
                    log.error("Enquiry not found with id: {}", id);
                    return new ResourceNotFoundException("Enquiry not found with id: " + id);
                });
        enquiry.setStatus(status);
        enquiry.setUpdatedBy("SYSTEM");
        Enquiry updated = enquiryRepository.save(enquiry);
        log.info("Updated status for enquiry with id: {} to {}", id, status);
        return enquiryMapper.toResponseDTO(updated);
    }

    @Transactional
    public void deleteEnquiry(Long id) {
        log.debug("Deleting enquiry with id: {}", id);
        Enquiry enquiry = enquiryRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Enquiry not found with id: {}", id);
                    return new ResourceNotFoundException("Enquiry not found with id: " + id);
                });
        enquiry.setIsDeleted(true);
        enquiry.setUpdatedBy("SYSTEM");
        enquiryRepository.save(enquiry);
        log.info("Soft deleted enquiry with id: {}", id);
    }

    @Transactional
    public BulkImportResponseDTO bulkImportEnquiries(MultipartFile file,
                                                     BulkImportRequestDTO.ImportType importType) {
        log.info("Starting bulk import from CSV file: {} with type: {}",
                file.getOriginalFilename(), importType);
        try {
            List<EnquiryRequestDTO> dtos;
            if (importType == BulkImportRequestDTO.ImportType.OLD_FORMAT) {
                dtos = csvService.parseOldFormatCSV(file);
            } else {
                dtos = csvService.parseNewFormatCSV(file);
            }
            return processBulkImport(dtos, importType.name());
        } catch (Exception e) {
            log.error("Error during bulk import", e);
            return BulkImportResponseDTO.builder()
                    .success(false)
                    .totalRecords(0)
                    .successfulImports(0)
                    .failedImports(0)
                    .message("Failed to process CSV file: " + e.getMessage())
                    .build();
        }
    }

    @Transactional(readOnly = true)
    public byte[] exportEnquiriesToCSV() {
        log.info("Exporting enquiries to CSV");
        List<Enquiry> enquiries = enquiryRepository.findAll(
                Sort.by("enquiryDate").descending()
        );
        List<EnquiryResponseDTO> dtos = enquiries.stream()
                .filter(e -> !e.getIsDeleted())
                .map(enquiryMapper::toResponseDTO)
                .collect(Collectors.toList());
        return csvService.generateCSV(dtos);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getEnquiryStatistics() {
        log.debug("Calculating enquiry statistics");
        Map<String, Object> stats = new HashMap<>();
        List<Object[]> statusStats = enquiryRepository.getEnquiryStatsByStatus();
        Map<String, Long> statusMap = statusStats.stream()
                .collect(Collectors.toMap(
                        arr -> (String) arr[0],
                        arr -> (Long) arr[1]
                ));
        stats.put("byStatus", statusMap);
        List<Object[]> sourceStats = enquiryRepository.getEnquiryStatsBySource();
        Map<String, Long> sourceMap = sourceStats.stream()
                .collect(Collectors.toMap(
                        arr -> (String) arr[0],
                        arr -> (Long) arr[1]
                ));
        stats.put("bySource", sourceMap);
        long total = enquiryRepository.count();
        stats.put("total", total);
        List<Enquiry> pendingFollowups = enquiryRepository
                .findPendingFollowups(LocalDate.now());
        stats.put("pendingFollowups", pendingFollowups.size());
        log.info("Generated statistics: {}", stats);
        return stats;
    }

    // ==================== LENIENT BULK IMPORT - IMPORT ALL DATA ====================

    /**
     * 🔥 LENIENT MODE: Import ALL rows regardless of validation errors
     * - Missing mobile → Generate placeholder
     * - Duplicate mobile → Append row number
     * - Missing courses → Add placeholder
     * - Invalid data → Import with notes
     *
     * GOAL: 0 ROWS SKIPPED, ALL DATA IN DATABASE
     *
     * NOTE: No @Transactional here - each record saved in its own transaction
     */
    public BulkImportResponseDTO processBulkImport(List<EnquiryRequestDTO> dtos, String importSource) {
        log.info("🔄 LENIENT BULK IMPORT: Processing {} records", dtos.size());
        log.info("📌 MODE: Import ALL data - No rows will be skipped");

        int successCount = 0;
        int withWarnings = 0;
        List<BulkImportResponseDTO.ImportError> errors = new ArrayList<>();

        for (int i = 0; i < dtos.size(); i++) {
            final int rowNumber = i + 2; // CSV row (header = 1)
            EnquiryRequestDTO dto = dtos.get(i);
            boolean hasWarnings = false;

            try {
                // ============ STEP 1: FIX MISSING/INVALID MOBILE ============
                String originalMobile = dto.getMobile();

                if (originalMobile == null || originalMobile.trim().isEmpty() ||
                        !originalMobile.matches("^[6-9]\\d{9}$")) {

                    String placeholderMobile = String.format("9999%06d", rowNumber);
                    dto.setMobile(placeholderMobile);

                    String note = dto.getNote() != null ? dto.getNote() + "\n" : "";
                    note += "[INVALID MOBILE: Original='" + originalMobile + "', Placeholder='" + placeholderMobile + "']";
                    dto.setNote(note);

                    hasWarnings = true;
                    log.warn("⚠️ Row {}: Invalid mobile '{}' → Using placeholder '{}'",
                            rowNumber, originalMobile, placeholderMobile);

                    errors.add(BulkImportResponseDTO.ImportError.builder()
                            .rowNumber(rowNumber)
                            .fieldName("mobile")
                            .errorMessage("Invalid mobile - using placeholder")
                            .rejectedValue(originalMobile)
                            .build());
                }

                // ============ STEP 2: HANDLE DUPLICATE MOBILE ============
                if (enquiryRepository.existsByMobileAndIsDeletedFalse(dto.getMobile())) {
                    String duplicateMobile = dto.getMobile();

                    String note = dto.getNote() != null ? dto.getNote() + "\n" : "";
                    note += "[DUPLICATE MOBILE: Another enquiry exists with mobile '" + duplicateMobile + "']";
                    dto.setNote(note);

                    hasWarnings = true;
                    log.warn("⚠️ Row {}: Duplicate mobile '{}' - Importing anyway",
                            rowNumber, duplicateMobile);

                    errors.add(BulkImportResponseDTO.ImportError.builder()
                            .rowNumber(rowNumber)
                            .fieldName("mobile")
                            .errorMessage("Duplicate mobile number - imported anyway")
                            .rejectedValue(duplicateMobile)
                            .build());
                    
                }

                // ============ STEP 3: FIX MISSING COURSES ============
                if (dto.getCourses() == null || dto.getCourses().isEmpty()) {
                    dto.setCourses(List.of("Not Specified"));

                    String note = dto.getNote() != null ? dto.getNote() + "\n" : "";
                    note += "[MISSING COURSES: Added placeholder 'Not Specified']";
                    dto.setNote(note);

                    hasWarnings = true;
                    log.warn("⚠️ Row {}: Missing courses → Added placeholder", rowNumber);

                    errors.add(BulkImportResponseDTO.ImportError.builder()
                            .rowNumber(rowNumber)
                            .fieldName("courses")
                            .errorMessage("Missing required fields (mobile or courses)")
                            .rejectedValue(dto.getMobile())
                            .build());
                }

                // ============ STEP 4: FIX MISSING NAME ============
                if ((dto.getFirstName() == null || dto.getFirstName().trim().isEmpty()) &&
                        (dto.getLastName() == null || dto.getLastName().trim().isEmpty()) &&
                        (dto.getName() == null || dto.getName().trim().isEmpty())) {

                    dto.setFirstName("Unknown");
                    dto.setLastName("Student");
                    dto.setName("Unknown Student");

                    String note = dto.getNote() != null ? dto.getNote() + "\n" : "";
                    note += "[MISSING NAME: Using 'Unknown Student']";
                    dto.setNote(note);

                    hasWarnings = true;
                    log.warn("⚠️ Row {}: Missing name → Using 'Unknown Student'", rowNumber);
                }

                // ============ STEP 5: SET DEFAULTS ============
                if (dto.getEnquiryDate() == null) {
                    dto.setEnquiryDate(LocalDate.now());
                }
                if (dto.getSource() == null || dto.getSource().trim().isEmpty()) {
                    dto.setSource("Unknown");
                }
                if (dto.getStatus() == null || dto.getStatus().trim().isEmpty()) {
                    dto.setStatus("New");
                }

                // ============ STEP 6: CONVERT AND SAVE ============
                Enquiry enquiry = convertDTOToEnquiry(dto, importSource);

                // Save to database in separate transaction
                try {
                    saveEnquiryInNewTransaction(enquiry);
                    successCount++;
                    if (hasWarnings) {
                        withWarnings++;
                    }

                    log.info("✅ Row {}: Imported {} (Mobile: {}, Courses: {})",
                            rowNumber,
                            hasWarnings ? "WITH WARNINGS" : "SUCCESSFULLY",
                            dto.getMobile(),
                            dto.getCourses().size());
                } catch (Exception saveEx) {
                    throw saveEx; // Let outer catch handle it
                }

            } catch (Exception e) {
                // Even if save fails, we tried our best - log it
                log.error("❌ Row {}: FAILED to save - {}", rowNumber, e.getMessage());

                errors.add(BulkImportResponseDTO.ImportError.builder()
                        .rowNumber(rowNumber)
                        .fieldName("database")
                        .errorMessage("Database save failed: " + e.getMessage())
                        .rejectedValue(dto != null ? dto.getMobile() : "unknown")
                        .build());
            }
        }

        int failedCount = dtos.size() - successCount;

        log.info("📊 ==================== IMPORT COMPLETE ====================");
        log.info("   Total Records: {}", dtos.size());
        log.info("   ✅ Successfully Imported: {}", successCount);
        log.info("   ⚠️  With Warnings/Fixes: {}", withWarnings);
        log.info("   ❌ Failed (DB Errors): {}", failedCount);
        log.info("   Success Rate: {}%", (successCount * 100 / dtos.size()));
        log.info("==========================================================");

        return BulkImportResponseDTO.builder()
                .success(successCount > 0)
                .totalRecords(dtos.size())
                .successfulImports(successCount)
                .failedImports(failedCount)
                .errors(errors)
                .message(String.format(
                        "Import completed: %d/%d successful (%d with warnings, %d failed)",
                        successCount, dtos.size(), withWarnings, failedCount
                ))
                .build();
    }

    /**
     * Convert DTO to Entity - NO VALIDATION, just conversion
     */
    private Enquiry convertDTOToEnquiry(EnquiryRequestDTO dto, String importSource) {
        Enquiry enquiry = enquiryMapper.toEntity(dto);

        // Set metadata
        enquiry.setImportSource(importSource);
        enquiry.setCreatedBy("BULK_IMPORT");

        // Ensure required fields have values (already fixed in processBulkImport)
        if (enquiry.getEnquiryDate() == null) {
            enquiry.setEnquiryDate(LocalDate.now());
        }
        if (enquiry.getSource() == null || enquiry.getSource().isBlank()) {
            enquiry.setSource("Unknown");
        }
        if (enquiry.getStatus() == null || enquiry.getStatus().isBlank()) {
            enquiry.setStatus("New");
        }
        if (enquiry.getCourses() == null || enquiry.getCourses().isEmpty()) {
            enquiry.setCourses(new ArrayList<>(List.of("Not Specified")));
        }

        return enquiry;
    }

    /**
     * Save enquiry in a NEW transaction to prevent rollback of entire batch
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveEnquiryInNewTransaction(Enquiry enquiry) {
        enquiryRepository.save(enquiry);
    }
}