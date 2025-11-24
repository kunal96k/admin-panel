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

    /**
     * Get all enquiries with pagination
     */
    @Transactional(readOnly = true)
    public Page<EnquiryResponseDTO> getAllEnquiries(int page, int size) {
        log.debug("Fetching enquiries - page: {}, size: {}", page, size);

        Pageable pageable = PageRequest.of(page, size, Sort.by("enquiryDate").descending());
        Page<Enquiry> enquiries = enquiryRepository.findByIsDeletedFalse(pageable);

        log.info("Retrieved {} enquiries out of {} total",
                enquiries.getNumberOfElements(), enquiries.getTotalElements());

        return enquiries.map(enquiryMapper::toResponseDTO);
    }

    /**
     * Search enquiries with filters
     */
    @Transactional(readOnly = true)
    public Page<EnquiryResponseDTO> searchEnquiries(EnquirySearchDTO searchDTO) {
        log.debug("Searching enquiries with criteria: {}", searchDTO);

        Sort sort = Sort.by(
                "DESC".equalsIgnoreCase(searchDTO.getSortDirection())
                        ? Sort.Direction.DESC
                        : Sort.Direction.ASC,
                searchDTO.getSortBy()
        );

        Pageable pageable = PageRequest.of(
                searchDTO.getPage(),
                searchDTO.getSize(),
                sort
        );

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

    /**
     * Get enquiry by ID
     */
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

    /**
     * Create new enquiry
     */
    @Transactional
    public EnquiryResponseDTO createEnquiry(EnquiryRequestDTO requestDTO) {
        log.debug("Creating new enquiry for mobile: {}", requestDTO.getMobile());

        // Check for duplicate mobile number
        if (enquiryRepository.existsByMobileAndIsDeletedFalse(requestDTO.getMobile())) {
            log.warn("Enquiry with mobile {} already exists", requestDTO.getMobile());
            throw new IllegalArgumentException(
                    "Enquiry with mobile number " + requestDTO.getMobile() + " already exists"
            );
        }

        Enquiry enquiry = enquiryMapper.toEntity(requestDTO);
        enquiry.setImportSource("MANUAL");
        enquiry.setCreatedBy("SYSTEM"); // TODO: Get from security context

        Enquiry saved = enquiryRepository.save(enquiry);
        log.info("Created enquiry with id: {} for {}", saved.getId(), saved.getDisplayName());

        return enquiryMapper.toResponseDTO(saved);
    }

    /**
     * Update existing enquiry
     */
    @Transactional
    public EnquiryResponseDTO updateEnquiry(Long id, EnquiryRequestDTO requestDTO) {
        log.debug("Updating enquiry with id: {}", id);

        Enquiry existingEnquiry = enquiryRepository.findById(id)
                .filter(e -> !e.getIsDeleted())
                .orElseThrow(() -> {
                    log.error("Enquiry not found with id: {}", id);
                    return new ResourceNotFoundException("Enquiry not found with id: " + id);
                });

        // Check if mobile is being changed and if new mobile already exists
        if (!existingEnquiry.getMobile().equals(requestDTO.getMobile()) &&
                enquiryRepository.existsByMobileAndIsDeletedFalse(requestDTO.getMobile())) {
            log.warn("Mobile number {} already exists for another enquiry", requestDTO.getMobile());
            throw new IllegalArgumentException(
                    "Mobile number " + requestDTO.getMobile() + " already exists"
            );
        }

        enquiryMapper.updateEntityFromDTO(requestDTO, existingEnquiry);
        existingEnquiry.setUpdatedBy("SYSTEM"); // TODO: Get from security context

        Enquiry updated = enquiryRepository.save(existingEnquiry);
        log.info("Updated enquiry with id: {}", id);

        return enquiryMapper.toResponseDTO(updated);
    }

    /**
     * Update enquiry status
     */
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
        enquiry.setUpdatedBy("SYSTEM"); // TODO: Get from security context

        Enquiry updated = enquiryRepository.save(enquiry);
        log.info("Updated status for enquiry with id: {} to {}", id, status);

        return enquiryMapper.toResponseDTO(updated);
    }

    /**
     * Delete enquiry (soft delete)
     */
    @Transactional
    public void deleteEnquiry(Long id) {
        log.debug("Deleting enquiry with id: {}", id);

        Enquiry enquiry = enquiryRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Enquiry not found with id: {}", id);
                    return new ResourceNotFoundException("Enquiry not found with id: " + id);
                });

        enquiry.setIsDeleted(true);
        enquiry.setUpdatedBy("SYSTEM"); // TODO: Get from security context

        enquiryRepository.save(enquiry);
        log.info("Soft deleted enquiry with id: {}", id);
    }

    /**
     * Bulk import enquiries from CSV
     */
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

    /**
     * Export enquiries to CSV
     */
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

    /**
     * Get enquiry statistics
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getEnquiryStatistics() {
        log.debug("Calculating enquiry statistics");

        Map<String, Object> stats = new HashMap<>();

        // Status-wise count
        List<Object[]> statusStats = enquiryRepository.getEnquiryStatsByStatus();
        Map<String, Long> statusMap = statusStats.stream()
                .collect(Collectors.toMap(
                        arr -> (String) arr[0],
                        arr -> (Long) arr[1]
                ));
        stats.put("byStatus", statusMap);

        // Source-wise count
        List<Object[]> sourceStats = enquiryRepository.getEnquiryStatsBySource();
        Map<String, Long> sourceMap = sourceStats.stream()
                .collect(Collectors.toMap(
                        arr -> (String) arr[0],
                        arr -> (Long) arr[1]
                ));
        stats.put("bySource", sourceMap);

        // Total count
        long total = enquiryRepository.count();
        stats.put("total", total);

        // Pending follow-ups
        List<Enquiry> pendingFollowups = enquiryRepository
                .findPendingFollowups(LocalDate.now());
        stats.put("pendingFollowups", pendingFollowups.size());

        log.info("Generated statistics: {}", stats);
        return stats;
    }

    /**
     * Simple bulk import - one transaction, skip failures
     */
    @Transactional
    public BulkImportResponseDTO processBulkImport(List<EnquiryRequestDTO> dtos, String importSource) {
        log.info("Processing bulk import of {} records", dtos.size());

        int successCount = 0;
        List<BulkImportResponseDTO.ImportError> errors = new ArrayList<>();

        for (int i = 0; i < dtos.size(); i++) {
            final int rowNumber = i + 2;
            EnquiryRequestDTO dto = dtos.get(i);

            try {
                // Skip if no mobile or courses
                if (dto.getMobile() == null || dto.getMobile().trim().isEmpty() ||
                        dto.getCourses() == null || dto.getCourses().isEmpty()) {
                    log.warn("Row {} - Missing required fields", rowNumber);
                    continue;
                }

                // Skip duplicates
                if (enquiryRepository.existsByMobileAndIsDeletedFalse(dto.getMobile())) {
                    log.warn("Row {} - Duplicate mobile: {}", rowNumber, dto.getMobile());
                    errors.add(BulkImportResponseDTO.ImportError.builder()
                            .rowNumber(rowNumber)
                            .fieldName("mobile")
                            .errorMessage("Duplicate mobile number")
                            .rejectedValue(dto.getMobile())
                            .build());
                    continue;
                }

                // Map and save
                Enquiry enquiry = enquiryMapper.toEntity(dto);
                enquiry.setImportSource(importSource);
                enquiry.setCreatedBy("BULK_IMPORT");

                enquiryRepository.save(enquiry);
                successCount++;

                log.info("✓ Row {} imported successfully", rowNumber);

            } catch (Exception e) {
                log.error("Row {} failed: {}", rowNumber, e.getMessage());
                errors.add(BulkImportResponseDTO.ImportError.builder()
                        .rowNumber(rowNumber)
                        .errorMessage(e.getMessage())
                        .rejectedValue(dto != null ? dto.getMobile() : "unknown")
                        .build());
            }
        }

        log.info("Import completed - Success: {}, Failed: {}", successCount, errors.size());

        return BulkImportResponseDTO.builder()
                .success(successCount > 0)
                .totalRecords(dtos.size())
                .successfulImports(successCount)
                .failedImports(errors.size())
                .errors(errors)
                .message(String.format("Imported %d/%d enquiries", successCount, dtos.size()))
                .build();
    }

    /**
     * Save enquiry in a new transaction to isolate failures
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void saveEnquiryInNewTransaction(EnquiryRequestDTO dto, String importSource) {
        // Convert DTO to entity
        Enquiry enquiry = enquiryMapper.toEntity(dto);

        // Ensure courses list is properly set
        if (enquiry.getCourses() == null || enquiry.getCourses().isEmpty()) {
            enquiry.setCourses(new ArrayList<>(dto.getCourses()));
        }

        enquiry.setImportSource(importSource);
        enquiry.setCreatedBy("BULK_IMPORT");

        // Log before save
        log.debug("Saving enquiry - Mobile: {}, Courses: {}",
                enquiry.getMobile(), enquiry.getCourses());

        // Save entity
        enquiryRepository.save(enquiry);
    }
}