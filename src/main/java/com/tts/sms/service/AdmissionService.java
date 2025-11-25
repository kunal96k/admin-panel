package com.tts.sms.service;

import com.tts.sms.dto.*;
import com.tts.sms.exception.ResourceNotFoundException;
import com.tts.sms.model.Admission;
import com.tts.sms.model.Enquiry;
import com.tts.sms.model.FeeInstallment;
import com.tts.sms.repository.AdmissionRepository;
import com.tts.sms.repository.EnquiryRepository;
import com.tts.sms.repository.FeeInstallmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.Year;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdmissionService {

    private final AdmissionRepository admissionRepository;
    private final EnquiryRepository enquiryRepository;
    private final FeeInstallmentRepository feeInstallmentRepository;
    private final AdmissionMapper admissionMapper;
    private final CSVService csvService;

    @Transactional(readOnly = true)
    public Page<AdmissionResponseDTO> getAllAdmissions(int page, int size) {
        log.debug("Fetching admissions - page: {}, size: {}", page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by("admissionDate").descending());
        Page<Admission> admissions = admissionRepository.findByIsDeletedFalse(pageable);
        log.info("Retrieved {} admissions out of {} total",
                admissions.getNumberOfElements(), admissions.getTotalElements());
        return admissions.map(this::toResponseDTOWithInstallments);
    }

    @Transactional(readOnly = true)
    public Page<AdmissionResponseDTO> searchAdmissions(AdmissionSearchDTO searchDTO) {
        log.debug("Searching admissions with criteria: {}", searchDTO);

        Sort sort = Sort.by(
                "DESC".equalsIgnoreCase(searchDTO.getSortDirection())
                        ? Sort.Direction.DESC
                        : Sort.Direction.ASC,
                searchDTO.getSortBy()
        );

        Pageable pageable = PageRequest.of(searchDTO.getPage(), searchDTO.getSize(), sort);

        // Pass parameters in correct order matching native query
        Page<Admission> results = admissionRepository.advancedSearch(
                searchDTO.getSearchTerm(), searchDTO.getSearchTerm(),
                searchDTO.getSearchTerm(), searchDTO.getSearchTerm(),
                searchDTO.getSearchTerm(), searchDTO.getSearchTerm(),
                searchDTO.getStatus(), searchDTO.getStatus(),
                searchDTO.getCourse(), searchDTO.getCourse(),
                searchDTO.getBatch(), searchDTO.getBatch(),
                searchDTO.getAcademicYear(), searchDTO.getAcademicYear(),
                searchDTO.getAdmissionDateFrom(), searchDTO.getAdmissionDateFrom(),
                searchDTO.getAdmissionDateTo(), searchDTO.getAdmissionDateTo(),
                pageable
        );

        log.info("Search returned {} results", results.getTotalElements());
        return results.map(this::toResponseDTOWithInstallments);
    }

    @Transactional(readOnly = true)
    public AdmissionResponseDTO getAdmissionById(Long id) {
        log.debug("Fetching admission with id: {}", id);
        Admission admission = admissionRepository.findById(id)
                .filter(a -> !a.getIsDeleted())
                .orElseThrow(() -> {
                    log.error("Admission not found with id: {}", id);
                    return new ResourceNotFoundException("Admission not found with id: " + id);
                });
        log.info("Retrieved admission: {}", admission.getFullName());
        return toResponseDTOWithInstallments(admission);
    }

    @Transactional
    public AdmissionResponseDTO createAdmission(AdmissionRequestDTO requestDTO) {
        log.debug("Creating new admission for mobile: {}", requestDTO.getMobilePrimary());

        // Check if enquiry exists (optional for bulk import)
        Long enquiryId = null;
        Optional<Enquiry> enquiryOpt = enquiryRepository
                .findByMobileAndIsDeletedFalse(requestDTO.getMobilePrimary());

        if (enquiryOpt.isPresent()) {
            enquiryId = enquiryOpt.get().getId();
            log.info("Found enquiry with ID: {} for mobile: {}", enquiryId, requestDTO.getMobilePrimary());
        } else {
            log.warn("No enquiry found for mobile: {} - Creating admission without enquiry link",
                    requestDTO.getMobilePrimary());
        }

        // Create admission entity
        Admission admission = admissionMapper.toEntity(requestDTO);
        admission.setEnquiryId(enquiryId);

        // Use registration number from CSV if provided, otherwise generate
        if (requestDTO.getRegistrationNumber() != null && !requestDTO.getRegistrationNumber().trim().isEmpty()) {
            admission.setRegistrationNumber(requestDTO.getRegistrationNumber());
        } else {
            admission.setRegistrationNumber(generateRegistrationNumber());
        }

        // Set defaults
        admission.setCreatedBy("SYSTEM");

        // Save admission
        Admission savedAdmission = admissionRepository.save(admission);
        log.info("Created admission with id: {} and reg no: {}",
                savedAdmission.getId(), savedAdmission.getRegistrationNumber());

        // Update enquiry status if exists
        if (enquiryId != null) {
            Enquiry enquiry = enquiryOpt.get();
            enquiry.setStatus("Admitted");
            enquiryRepository.save(enquiry);
        }

        // Generate fee installments if config provided
        if (requestDTO.getInstallmentConfig() != null) {
            generateInstallments(savedAdmission.getId(), requestDTO.getInstallmentConfig(),
                    requestDTO.getTotalReceivableFees());
        }

        return toResponseDTOWithInstallments(savedAdmission);
    }

    @Transactional
    public AdmissionResponseDTO updateAdmission(Long id, AdmissionRequestDTO requestDTO) {
        log.debug("Updating admission with id: {}", id);

        Admission existingAdmission = admissionRepository.findById(id)
                .filter(a -> !a.getIsDeleted())
                .orElseThrow(() -> {
                    log.error("Admission not found with id: {}", id);
                    return new ResourceNotFoundException("Admission not found with id: " + id);
                });

        admissionMapper.updateEntityFromDTO(requestDTO, existingAdmission);
        existingAdmission.setUpdatedBy("SYSTEM");

        Admission updated = admissionRepository.save(existingAdmission);
        log.info("Updated admission with id: {}", id);

        return toResponseDTOWithInstallments(updated);
    }

    @Transactional
    public AdmissionResponseDTO transferAdmission(TransferAdmissionDTO transferDTO) {
        log.debug("Transferring admission with id: {}", transferDTO.getAdmissionId());

        Admission admission = admissionRepository.findById(transferDTO.getAdmissionId())
                .filter(a -> !a.getIsDeleted())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Admission not found with id: " + transferDTO.getAdmissionId()));

        // Update transfer details
        admission.setAcademicYear(transferDTO.getAcademicYear());

        if (transferDTO.getCourses() != null && !transferDTO.getCourses().isEmpty()) {
            admission.setCourses(transferDTO.getCourses());
        }

        if (transferDTO.getBatches() != null && !transferDTO.getBatches().isEmpty()) {
            admission.setBatches(transferDTO.getBatches());
        }

        if (transferDTO.getSubjects() != null && !transferDTO.getSubjects().isEmpty()) {
            admission.setSubjects(transferDTO.getSubjects());
        }

        if (transferDTO.getPackageName() != null) {
            admission.setPackageName(transferDTO.getPackageName());
        }

        if (transferDTO.getTotalPayableFees() != null) {
            admission.setTotalPayableFees(transferDTO.getTotalPayableFees());
        }

        if (transferDTO.getTotalReceivableFees() != null) {
            admission.setTotalReceivableFees(transferDTO.getTotalReceivableFees());
        }

        admission.setDiscountPercent(transferDTO.getDiscountPercent());
        admission.setDiscountAmount(transferDTO.getDiscountAmount());

        // Append transfer reason to notes
        String transferNote = String.format(
                "\n[Transfer on %s] Academic Year: %s. Reason: %s",
                transferDTO.getTransferDate(),
                transferDTO.getAcademicYear(),
                transferDTO.getTransferReason() != null ? transferDTO.getTransferReason() : "N/A"
        );
        admission.setNotes(
                (admission.getNotes() != null ? admission.getNotes() : "") + transferNote
        );

        admission.setUpdatedBy("SYSTEM");

        Admission transferred = admissionRepository.save(admission);
        log.info("Transferred admission with id: {} to academic year: {}",
                admission.getId(), transferDTO.getAcademicYear());

        return toResponseDTOWithInstallments(transferred);
    }

    @Transactional
    public void deleteAdmission(Long id) {
        log.debug("Deleting admission with id: {}", id);

        Admission admission = admissionRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Admission not found with id: {}", id);
                    return new ResourceNotFoundException("Admission not found with id: " + id);
                });

        admission.setIsDeleted(true);
        admission.setDeletedAt(java.time.LocalDateTime.now());
        admission.setUpdatedBy("SYSTEM");

        admissionRepository.save(admission);
        log.info("Soft deleted admission with id: {}", id);
    }

    @Transactional
    public List<FeeInstallmentDTO> generateInstallments(Long admissionId,
                                                        InstallmentConfigDTO config,
                                                        Double totalAmount) {
        log.debug("Generating {} installments for admission: {}",
                config.getNumberOfInstallments(), admissionId);

        // Delete existing installments
        feeInstallmentRepository.deleteByAdmissionId(admissionId);

        List<FeeInstallment> installments = new ArrayList<>();
        Double amountPerInstallment = totalAmount / config.getNumberOfInstallments();
        LocalDate currentDate = config.getStartDate();

        for (int i = 1; i <= config.getNumberOfInstallments(); i++) {
            FeeInstallment installment = FeeInstallment.builder()
                    .admissionId(admissionId)
                    .installmentNumber(i)
                    .dueDate(currentDate)
                    .amount(amountPerInstallment)
                    .status("Pending")
                    .createdBy("SYSTEM")
                    .build();

            installments.add(installment);
            currentDate = currentDate.plusDays(config.getDaysBetween());
        }

        List<FeeInstallment> saved = feeInstallmentRepository.saveAll(installments);
        log.info("Generated {} installments for admission: {}", saved.size(), admissionId);

        return saved.stream()
                .map(admissionMapper::toInstallmentDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<FeeInstallmentDTO> getInstallments(Long admissionId) {
        log.debug("Fetching installments for admission: {}", admissionId);

        List<FeeInstallment> installments =
                feeInstallmentRepository.findByAdmissionIdOrderByDueDateAsc(admissionId);

        return installments.stream()
                .map(admissionMapper::toInstallmentDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public boolean canCreateAdmission(String mobileNumber) {
        log.debug("Checking if admission can be created for mobile: {}", mobileNumber);

        boolean enquiryExists = enquiryRepository.existsByMobileAndIsDeletedFalse(mobileNumber);

        if (!enquiryExists) {
            log.warn("No enquiry found for mobile: {}", mobileNumber);
            return false;
        }

        boolean admissionExists = admissionRepository.existsByMobilePrimaryAndIsDeletedFalse(mobileNumber);

        if (admissionExists) {
            log.warn("Admission already exists for mobile: {}", mobileNumber);
            return false;
        }

        return true;
    }

    @Transactional(readOnly = true)
    public EnquiryResponseDTO getEnquiryForAdmission(String mobileNumber) {
        log.debug("Fetching enquiry data for mobile: {}", mobileNumber);

        Enquiry enquiry = enquiryRepository
                .findByMobileAndIsDeletedFalse(mobileNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No enquiry found for mobile: " + mobileNumber));

        return EnquiryResponseDTO.builder()
                .id(enquiry.getId())
                .firstName(enquiry.getFirstName())
                .middleName(enquiry.getMiddleName())
                .lastName(enquiry.getLastName())
                .mobile(enquiry.getMobile())
                .secondaryMobile(enquiry.getSecondaryMobile())
                .email(enquiry.getEmail())
                .currentAddress(enquiry.getCurrentAddress())
                .college(enquiry.getCollege())
                .qualification(enquiry.getQualification())
                .aadhaar(enquiry.getAadhaar())
                .birthDate(enquiry.getBirthDate())
                .gender(enquiry.getGender())
                .coursesList(enquiry.getCourses())
                .source(enquiry.getSource())
                .build();
    }

    private String generateRegistrationNumber() {
        String prefix = "ADM" + Year.now().getValue();
        String maxRegNo = admissionRepository.findMaxRegistrationNumber(prefix);

        int nextNumber = 1;
        if (maxRegNo != null && maxRegNo.length() > prefix.length()) {
            try {
                String numberPart = maxRegNo.substring(prefix.length());
                nextNumber = Integer.parseInt(numberPart) + 1;
            } catch (NumberFormatException e) {
                log.warn("Error parsing registration number: {}", maxRegNo);
            }
        }

        return String.format("%s%04d", prefix, nextNumber);
    }

    private AdmissionResponseDTO toResponseDTOWithInstallments(Admission admission) {
        AdmissionResponseDTO dto = admissionMapper.toResponseDTO(admission);

        List<FeeInstallment> installments =
                feeInstallmentRepository.findByAdmissionIdOrderByDueDateAsc(admission.getId());

        dto.setInstallments(installments.stream()
                .map(admissionMapper::toInstallmentDTO)
                .collect(Collectors.toList()));

        dto.setTotalInstallments(installments.size());

        Double totalPaid = feeInstallmentRepository.getTotalPaidAmount(admission.getId());
        Double totalDue = feeInstallmentRepository.getTotalDueAmount(admission.getId());

        dto.setTotalPaidAmount(totalPaid != null ? totalPaid : 0.0);
        dto.setTotalDueAmount(totalDue != null ? totalDue : 0.0);

        return dto;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAdmissionStatistics() {
        log.debug("Calculating admission statistics");

        Map<String, Object> stats = new HashMap<>();

        List<Object[]> statusStats = admissionRepository.getAdmissionStatsByStatus();
        Map<String, Long> statusMap = statusStats.stream()
                .collect(Collectors.toMap(
                        arr -> (String) arr[0],
                        arr -> (Long) arr[1]
                ));
        stats.put("byStatus", statusMap);

        List<Object[]> yearStats = admissionRepository.getAdmissionStatsByYear();
        Map<String, Long> yearMap = yearStats.stream()
                .collect(Collectors.toMap(
                        arr -> (String) arr[0],
                        arr -> (Long) arr[1]
                ));
        stats.put("byYear", yearMap);

        long total = admissionRepository.count();
        stats.put("total", total);

        log.info("Generated statistics: {}", stats);
        return stats;
    }

    // ==================== LENIENT BULK IMPORT ====================

    @Transactional
    public BulkImportResponseDTO bulkImportAdmissions(MultipartFile file, String importType) {
        log.info("Starting bulk admission import from CSV file: {}", file.getOriginalFilename());

        try {
            List<AdmissionRequestDTO> dtos;

            if ("OLD_FORMAT".equals(importType)) {
                dtos = csvService.parseOldFormatAdmissionCSV(file);
            } else {
                throw new IllegalArgumentException("Only OLD_FORMAT is currently supported");
            }

            return processBulkAdmissionImport(dtos, importType);

        } catch (Exception e) {
            log.error("Error during bulk admission import", e);
            return BulkImportResponseDTO.builder()
                    .success(false)
                    .totalRecords(0)
                    .successfulImports(0)
                    .failedImports(0)
                    .message("Failed to process CSV file: " + e.getMessage())
                    .build();
        }
    }

    @Transactional
    public BulkImportResponseDTO processBulkAdmissionImport(List<AdmissionRequestDTO> dtos, String importSource) {
        log.info("🔄 LENIENT ADMISSION IMPORT: Processing {} records", dtos.size());

        int successCount = 0;
        int withWarnings = 0;
        List<BulkImportResponseDTO.ImportError> errors = new ArrayList<>();

        for (int i = 0; i < dtos.size(); i++) {
            final int rowNumber = i + 2;
            AdmissionRequestDTO dto = dtos.get(i);
            boolean hasWarnings = false;

            try {
                // STEP 1: Fix missing/invalid mobile
                String originalMobile = dto.getMobilePrimary();

                if (originalMobile == null || originalMobile.trim().isEmpty() ||
                        !originalMobile.matches("^[6-9]\\d{9}$")) {

                    String placeholderMobile = String.format("8888%06d", rowNumber);
                    dto.setMobilePrimary(placeholderMobile);

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

                // STEP 2: Check for enquiry (optional)
                Long enquiryId = null;
                Optional<Enquiry> enquiryOpt = enquiryRepository
                        .findByMobileAndIsDeletedFalse(dto.getMobilePrimary());

                if (enquiryOpt.isPresent()) {
                    enquiryId = enquiryOpt.get().getId();
                } else {
                    hasWarnings = true;
                    log.warn("⚠️ Row {}: No enquiry found - Creating admission without enquiry link", rowNumber);

                    errors.add(BulkImportResponseDTO.ImportError.builder()
                            .rowNumber(rowNumber)
                            .fieldName("enquiry")
                            .errorMessage("No enquiry found - admission created independently")
                            .rejectedValue(dto.getMobilePrimary())
                            .build());
                }

                // STEP 3: Allow duplicates - No mobile uniqueness check
                // Duplicates are allowed in the system

               // STEP 4: Fix missing courses
                if (dto.getCourses() == null || dto.getCourses().isEmpty()) {
                    dto.setCourses(List.of("Not Specified"));
                    hasWarnings = true;
                }

                // STEP 5: Fix missing name
                if ((dto.getFirstName() == null || dto.getFirstName().trim().isEmpty()) &&
                        (dto.getLastName() == null || dto.getLastName().trim().isEmpty())) {

                    dto.setFirstName("Unknown");
                    dto.setLastName("Student");
                    hasWarnings = true;
                }

                // STEP 6: Set defaults
                if (dto.getAdmissionDate() == null) {
                    dto.setAdmissionDate(LocalDate.now());
                }
                if (dto.getLeadSource() == null || dto.getLeadSource().trim().isEmpty()) {
                    dto.setLeadSource("CSV_IMPORT");
                }
                if (dto.getAcademicYear() == null || dto.getAcademicYear().trim().isEmpty()) {
                    dto.setAcademicYear(String.valueOf(Year.now().getValue()));
                }
                if (dto.getDocumentType() == null || dto.getDocumentType().trim().isEmpty()) {
                    dto.setDocumentType("Aadhaar Card");
                }

                // STEP 7: Create admission
                Admission admission = admissionMapper.toEntity(dto);
                admission.setEnquiryId(enquiryId);
                admission.setRegistrationNumber(generateRegistrationNumber());
                admission.setCreatedBy("BULK_IMPORT");

                admissionRepository.save(admission);

                successCount++;
                if (hasWarnings) {
                    withWarnings++;
                }

                log.info("✅ Row {}: Imported {} (Mobile: {}, Reg: {})",
                        rowNumber,
                        hasWarnings ? "WITH WARNINGS" : "SUCCESSFULLY",
                        dto.getMobilePrimary(),
                        admission.getRegistrationNumber());

            } catch (Exception e) {
                log.error("❌ Row {}: FAILED to save - {}", rowNumber, e.getMessage());

                errors.add(BulkImportResponseDTO.ImportError.builder()
                        .rowNumber(rowNumber)
                        .fieldName("database")
                        .errorMessage("Database save failed: " + e.getMessage())
                        .rejectedValue(dto != null ? dto.getMobilePrimary() : "unknown")
                        .build());
            }
        }

        int failedCount = dtos.size() - successCount;

        log.info("📊 ADMISSION IMPORT COMPLETE: {}/{} successful ({} with warnings, {} failed)",
                successCount, dtos.size(), withWarnings, failedCount);

        return BulkImportResponseDTO.builder()
                .success(successCount > 0)
                .totalRecords(dtos.size())
                .successfulImports(successCount)
                .failedImports(failedCount)
                .errors(errors)
                .message(String.format(
                        "Admission import completed: %d/%d successful (%d with warnings, %d failed)",
                        successCount, dtos.size(), withWarnings, failedCount
                ))
                .build();
    }
}