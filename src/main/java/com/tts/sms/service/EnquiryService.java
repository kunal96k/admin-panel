package com.tts.sms.service;

import com.tts.sms.dto.*;
import com.tts.sms.model.Enquiry;
import com.tts.sms.exception.ResourceNotFoundException;
import com.tts.sms.repository.EnquiryRepository;
import com.tts.sms.specification.EnquirySpecifications;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
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
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "enquiryDate", "id"));
        Page<Enquiry> enquiries = enquiryRepository.findByIsDeletedFalse(pageable);
        log.info("Retrieved {} enquiries out of {} total",
                enquiries.getNumberOfElements(), enquiries.getTotalElements());
        return enquiries.map(enquiryMapper::toResponseDTO);
    }

    @Transactional(readOnly = true)
    public Page<EnquiryResponseDTO> searchEnquiries(EnquirySearchDTO searchDTO) {
        log.debug("Searching enquiries with criteria: {}", searchDTO);

        String sortColumn = "enquiryDate"; // Default to enquiryDate

        if ("createdAt".equals(searchDTO.getSortBy())) {
            sortColumn = "createdAt";
        } else if ("enquiryDate".equals(searchDTO.getSortBy())) {
            sortColumn = "enquiryDate";
        } else if ("firstName".equals(searchDTO.getSortBy())) {
            sortColumn = "firstName";
        } else if ("lastName".equals(searchDTO.getSortBy())) {
            sortColumn = "lastName";
        } else if ("mobile".equals(searchDTO.getSortBy())) {
            sortColumn = "mobile";
        }

        Sort.Direction direction = "DESC".equalsIgnoreCase(searchDTO.getSortDirection())
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;

        Sort sort = Sort.by(direction, sortColumn).and(Sort.by(Sort.Direction.DESC, "id"));

        Pageable pageable = PageRequest.of(searchDTO.getPage(), searchDTO.getSize(), sort);

        Specification<Enquiry> spec = EnquirySpecifications.getSearchSpecification(searchDTO);
        Page<Enquiry> results = enquiryRepository.findAll(spec, pageable);
        
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
        enquiry.setCreatedBy(getCurrentLoggedInUser());
        enquiry.setUpdatedBy(getCurrentLoggedInUser());
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
//        if (!existingEnquiry.getMobile().equals(requestDTO.getMobile()) &&
//                enquiryRepository.existsByMobileAndIsDeletedFalse(requestDTO.getMobile())) {
//            log.warn("Mobile number {} already exists for another enquiry", requestDTO.getMobile());
//            throw new IllegalArgumentException(
//                    "Mobile number " + requestDTO.getMobile() + " already exists"
//            );
//        }
        enquiryMapper.updateEntityFromDTO(requestDTO, existingEnquiry);
        existingEnquiry.setUpdatedBy(getCurrentLoggedInUser());
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
        enquiry.setUpdatedBy(getCurrentLoggedInUser());
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
        enquiry.setUpdatedBy(getCurrentLoggedInUser());
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
            } else if (importType == BulkImportRequestDTO.ImportType.COUNSELOR_FORMAT) {
                dtos = csvService.parseCounselorFormatCSV(file);
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
                Sort.by("createdAt").descending()
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

    public BulkImportResponseDTO processBulkImport(List<EnquiryRequestDTO> dtos, String importSource) {
        log.info("[START] STRICT IMPORT: {} records", dtos.size());
        log.info("[OK] MODE: Import ALL - No validation, no modification");

        int successCount = 0;
        int duplicateCount = 0;
        List<BulkImportResponseDTO.ImportError> errors = new ArrayList<>();
        Set<String> processedMobiles = new HashSet<>();
        String currentLoggedUser = getCurrentLoggedInUser();

        for (int i = 0; i < dtos.size(); i++) {
            final int rowNumber = i + 2;
            EnquiryRequestDTO dto = dtos.get(i);

            try {
                String originalMobile = dto.getMobile();
                String finalMobile = originalMobile;

                if (importSource.equalsIgnoreCase("COUNSELOR_FORMAT")) {
                    dto.setAssignTo(currentLoggedUser);
                    String cleanMobile = (originalMobile != null) ? originalMobile.trim() : "";
                    if (!cleanMobile.isEmpty() && !cleanMobile.equalsIgnoreCase("N/A") && !cleanMobile.equalsIgnoreCase("null")) {
                        // Check if exists in DB
                        Optional<Enquiry> existingEnquiryOpt = enquiryRepository.findFirstByMobileAndIsDeletedFalse(cleanMobile);
                        if (existingEnquiryOpt.isPresent()) {
                            Enquiry existingEnquiry = existingEnquiryOpt.get();
                            
                            // Update name fields
                            if (dto.getFirstName() != null && !dto.getFirstName().equalsIgnoreCase("N/A")) {
                                existingEnquiry.setFirstName(dto.getFirstName());
                            }
                            if (dto.getMiddleName() != null && !dto.getMiddleName().equalsIgnoreCase("N/A")) {
                                existingEnquiry.setMiddleName(dto.getMiddleName());
                            }
                            if (dto.getLastName() != null && !dto.getLastName().equalsIgnoreCase("N/A")) {
                                existingEnquiry.setLastName(dto.getLastName());
                            }
                            // Re-calculate full name
                            StringBuilder fullNameBuilder = new StringBuilder();
                            if (existingEnquiry.getFirstName() != null) fullNameBuilder.append(existingEnquiry.getFirstName());
                            if (existingEnquiry.getMiddleName() != null && !existingEnquiry.getMiddleName().equalsIgnoreCase("N/A")) {
                                if (fullNameBuilder.length() > 0) fullNameBuilder.append(" ");
                                fullNameBuilder.append(existingEnquiry.getMiddleName());
                            }
                            if (existingEnquiry.getLastName() != null) {
                                if (fullNameBuilder.length() > 0) fullNameBuilder.append(" ");
                                fullNameBuilder.append(existingEnquiry.getLastName());
                            }
                            existingEnquiry.setFullName(fullNameBuilder.toString());

                            // Update courses
                            if (dto.getCourses() != null && !dto.getCourses().isEmpty()) {
                                existingEnquiry.setCourses(dto.getCourses());
                            }

                            // Update source
                            if (dto.getSource() != null && !dto.getSource().equalsIgnoreCase("N/A")) {
                                existingEnquiry.setSource(dto.getSource());
                            }

                            // Update enquiry date
                            if (dto.getEnquiryDate() != null) {
                                existingEnquiry.setEnquiryDate(dto.getEnquiryDate());
                            }

                            // Update Counselor Name to the current logged-in user
                            existingEnquiry.setAssignTo(currentLoggedUser);
                            existingEnquiry.setUpdatedBy(currentLoggedUser);

                            // Save the updated enquiry
                            saveEnquiryInNewTransaction(existingEnquiry);
                            successCount++;
                            duplicateCount++;
                            continue;
                        }
                        processedMobiles.add(cleanMobile);
                    }
                } else {
                    // Handle duplicate mobile by appending suffix for other formats
                    List<Enquiry> existingEnquiries = enquiryRepository.findByMobileContaining(originalMobile);

                    if (!existingEnquiries.isEmpty()) {
                        int suffix = existingEnquiries.size() + 1;
                        finalMobile = originalMobile + "_" + suffix;
                        dto.setMobile(finalMobile);
                        duplicateCount++;

                        log.warn("[WARN] Row {}: Duplicate mobile '{}' -> '{}'",
                                rowNumber, originalMobile, finalMobile);

                        errors.add(BulkImportResponseDTO.ImportError.builder()
                                .rowNumber(rowNumber)
                                .fieldName("mobile")
                                .errorMessage("Duplicate - appended suffix")
                                .rejectedValue(originalMobile + " -> " + finalMobile)
                                .build());
                    }
                }

                // Convert DTO to Entity (no validation)
                Enquiry enquiry = convertDTOToEnquiry(dto, importSource);

                // Save in isolated transaction
                saveEnquiryInNewTransaction(enquiry);
                successCount++;

                if (rowNumber % 100 == 0) {
                    log.info("[OK] Progress: {}/{} imported", successCount, rowNumber - 1);
                }

            } catch (Exception e) {
                log.error("[FAIL] Row {}: Failed - {}", rowNumber, e.getMessage());

                errors.add(BulkImportResponseDTO.ImportError.builder()
                        .rowNumber(rowNumber)
                        .fieldName("database")
                        .errorMessage("Save failed: " + e.getMessage())
                        .rejectedValue(dto.getMobile())
                        .build());
            }
        }

        int failedCount = dtos.size() - successCount;

        log.info("[STATS] ==================== IMPORT COMPLETE ====================");
        log.info("   Total Records: {}", dtos.size());
        log.info("   [OK] Imported: {}", successCount);
        log.info("   [SYNC] Duplicates: {}", duplicateCount);
        log.info("   [FAIL] Failed: {}", failedCount);
        log.info("   [TREND] Success Rate: {}%", (dtos.isEmpty() ? 0 : (successCount * 100 / dtos.size())));
        log.info("==========================================================");

        return BulkImportResponseDTO.builder()
                .success(successCount > 0)
                .totalRecords(dtos.size())
                .successfulImports(successCount)
                .failedImports(failedCount)
                .duplicateCount(duplicateCount)
                .errors(errors)
                .message(String.format(
                        "Import completed: %d/%d successful (%d duplicates updated/appended, %d failed)",
                        successCount, dtos.size(), duplicateCount, failedCount
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

        // Ensure required fields have values (already ed in processBulkImport)
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

    private String getCurrentLoggedInUser() {
        try {
            org.springframework.security.core.Authentication authentication = 
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() && 
                !(authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)) {
                Object principal = authentication.getPrincipal();
                if (principal instanceof com.tts.sms.model.User) {
                    com.tts.sms.model.User user = (com.tts.sms.model.User) principal;
                    if (user.getEmployee() != null && user.getEmployee().getEmployeeName() != null) {
                        return user.getEmployee().getEmployeeName();
                    }
                    return user.getUsername();
                } else if (principal != null) {
                    return principal.toString();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get current logged in user: {}", e.getMessage());
        }
        return "SYSTEM";
    }
}