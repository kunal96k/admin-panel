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

    /**
     * Get all admissions with pagination
     */
    @Transactional(readOnly = true)
    public Page<AdmissionResponseDTO> getAllAdmissions(int page, int size) {
        log.debug("Fetching admissions - page: {}, size: {}", page, size);

        Pageable pageable = PageRequest.of(page, size,
                Sort.by("admissionDate").descending());
        Page<Admission> admissions = admissionRepository.findByIsDeletedFalse(pageable);

        log.info("Retrieved {} admissions out of {} total",
                admissions.getNumberOfElements(), admissions.getTotalElements());

        return admissions.map(this::toResponseDTOWithInstallments);
    }

    /**
     * Search admissions with filters
     */
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

        Page<Admission> results = admissionRepository.advancedSearch(
                searchDTO.getSearchTerm(),
                searchDTO.getStatus(),
                searchDTO.getCourse(),
                searchDTO.getBatch(),
                searchDTO.getAcademicYear(),
                searchDTO.getAdmissionDateFrom(),
                searchDTO.getAdmissionDateTo(),
                pageable
        );

        log.info("Search returned {} results", results.getTotalElements());
        return results.map(this::toResponseDTOWithInstallments);
    }

    /**
     * Get admission by ID
     */
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

    /**
     * Create new admission - REQUIRES ENQUIRY TO EXIST
     */
    @Transactional
    public AdmissionResponseDTO createAdmission(AdmissionRequestDTO requestDTO) {
        log.debug("Creating new admission for mobile: {}", requestDTO.getMobilePrimary());

        // CRITICAL: Check if enquiry exists for this mobile number
        Enquiry enquiry = enquiryRepository
                .findByMobileAndIsDeletedFalse(requestDTO.getMobilePrimary())
                .orElseThrow(() -> {
                    log.error("No enquiry found for mobile: {}", requestDTO.getMobilePrimary());
                    return new IllegalArgumentException(
                            "Cannot create admission. No enquiry found for mobile number: "
                                    + requestDTO.getMobilePrimary() +
                                    ". Please create an enquiry first."
                    );
                });

        log.info("Found enquiry with ID: {} for mobile: {}",
                enquiry.getId(), requestDTO.getMobilePrimary());

        // Check if admission already exists for this enquiry
        if (admissionRepository.existsByEnquiryIdAndIsDeletedFalse(enquiry.getId())) {
            log.warn("Admission already exists for enquiry: {}", enquiry.getId());
            throw new IllegalArgumentException(
                    "Admission already exists for this student (Enquiry ID: "
                            + enquiry.getId() + ")"
            );
        }

        // Check for duplicate mobile
        if (admissionRepository.existsByMobilePrimaryAndIsDeletedFalse(
                requestDTO.getMobilePrimary())) {
            log.warn("Admission with mobile {} already exists", requestDTO.getMobilePrimary());
            throw new IllegalArgumentException(
                    "Admission with mobile number " + requestDTO.getMobilePrimary()
                            + " already exists"
            );
        }

        // Create admission entity
        Admission admission = admissionMapper.toEntity(requestDTO);
        admission.setEnquiryId(enquiry.getId());

        // Generate registration number
        admission.setRegistrationNumber(generateRegistrationNumber());

        // Set defaults
        admission.setCreatedBy("SYSTEM"); // TODO: Get from security context

        // Save admission
        Admission savedAdmission = admissionRepository.save(admission);
        log.info("Created admission with id: {} and reg no: {}",
                savedAdmission.getId(), savedAdmission.getRegistrationNumber());

        // Generate fee installments if config provided
        if (requestDTO.getInstallmentConfig() != null) {
            generateInstallments(savedAdmission.getId(), requestDTO.getInstallmentConfig(),
                    requestDTO.getTotalReceivableFees());
        }

        // Update enquiry status to 'Admitted'
        enquiry.setStatus("Admitted");
        enquiryRepository.save(enquiry);

        return toResponseDTOWithInstallments(savedAdmission);
    }

    /**
     * Update existing admission
     */
    @Transactional
    public AdmissionResponseDTO updateAdmission(Long id, AdmissionRequestDTO requestDTO) {
        log.debug("Updating admission with id: {}", id);

        Admission existingAdmission = admissionRepository.findById(id)
                .filter(a -> !a.getIsDeleted())
                .orElseThrow(() -> {
                    log.error("Admission not found with id: {}", id);
                    return new ResourceNotFoundException("Admission not found with id: " + id);
                });

        // Check if mobile is being changed and if new mobile already exists
        if (!existingAdmission.getMobilePrimary().equals(requestDTO.getMobilePrimary()) &&
                admissionRepository.existsByMobilePrimaryAndIsDeletedFalse(
                        requestDTO.getMobilePrimary())) {
            log.warn("Mobile number {} already exists for another admission",
                    requestDTO.getMobilePrimary());
            throw new IllegalArgumentException(
                    "Mobile number " + requestDTO.getMobilePrimary() + " already exists"
            );
        }

        admissionMapper.updateEntityFromDTO(requestDTO, existingAdmission);
        existingAdmission.setUpdatedBy("SYSTEM"); // TODO: Get from security context

        Admission updated = admissionRepository.save(existingAdmission);
        log.info("Updated admission with id: {}", id);

        return toResponseDTOWithInstallments(updated);
    }

    /**
     * Transfer admission to new academic year/batch
     */
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
            admission.setCourses(Collections.singletonList(String.join(", ", transferDTO.getCourses())));
        }

        if (transferDTO.getBatches() != null && !transferDTO.getBatches().isEmpty()) {
            admission.setBatches(Collections.singletonList(String.join(", ", transferDTO.getBatches())));
        }

        if (transferDTO.getSubjects() != null && !transferDTO.getSubjects().isEmpty()) {
            admission.setSubjects(Collections.singletonList(String.join(", ", transferDTO.getSubjects())));
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

    /**
     * Delete admission (soft delete)
     */
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

    /**
     * Generate fee installments
     */
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

    /**
     * Get installments for admission
     */
    @Transactional(readOnly = true)
    public List<FeeInstallmentDTO> getInstallments(Long admissionId) {
        log.debug("Fetching installments for admission: {}", admissionId);

        List<FeeInstallment> installments =
                feeInstallmentRepository.findByAdmissionIdOrderByDueDateAsc(admissionId);

        return installments.stream()
                .map(admissionMapper::toInstallmentDTO)
                .collect(Collectors.toList());
    }

    /**
     * Check if enquiry exists before admission
     */
    @Transactional(readOnly = true)
    public boolean canCreateAdmission(String mobileNumber) {
        log.debug("Checking if admission can be created for mobile: {}", mobileNumber);

        // Check if enquiry exists
        boolean enquiryExists = enquiryRepository
                .existsByMobileAndIsDeletedFalse(mobileNumber);

        if (!enquiryExists) {
            log.warn("No enquiry found for mobile: {}", mobileNumber);
            return false;
        }

        // Check if admission already exists
        boolean admissionExists = admissionRepository
                .existsByMobilePrimaryAndIsDeletedFalse(mobileNumber);

        if (admissionExists) {
            log.warn("Admission already exists for mobile: {}", mobileNumber);
            return false;
        }

        return true;
    }

    /**
     * Get enquiry data for admission form pre-fill
     */
    @Transactional(readOnly = true)
    public EnquiryResponseDTO getEnquiryForAdmission(String mobileNumber) {
        log.debug("Fetching enquiry data for mobile: {}", mobileNumber);

        Enquiry enquiry = enquiryRepository
                .findByMobileAndIsDeletedFalse(mobileNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No enquiry found for mobile: " + mobileNumber));

        // Map to response (reuse EnquiryMapper)
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

    /**
     * Generate unique registration number
     */
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

    /**
     * Convert to DTO with installments
     */
    private AdmissionResponseDTO toResponseDTOWithInstallments(Admission admission) {
        AdmissionResponseDTO dto = admissionMapper.toResponseDTO(admission);

        // Fetch installments
        List<FeeInstallment> installments =
                feeInstallmentRepository.findByAdmissionIdOrderByDueDateAsc(admission.getId());

        dto.setInstallments(installments.stream()
                .map(admissionMapper::toInstallmentDTO)
                .collect(Collectors.toList()));

        dto.setTotalInstallments(installments.size());

        // Calculate totals
        Double totalPaid = feeInstallmentRepository.getTotalPaidAmount(admission.getId());
        Double totalDue = feeInstallmentRepository.getTotalDueAmount(admission.getId());

        dto.setTotalPaidAmount(totalPaid != null ? totalPaid : 0.0);
        dto.setTotalDueAmount(totalDue != null ? totalDue : 0.0);

        return dto;
    }

    /**
     * Get admission statistics
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getAdmissionStatistics() {
        log.debug("Calculating admission statistics");

        Map<String, Object> stats = new HashMap<>();

        // Status-wise count
        List<Object[]> statusStats = admissionRepository.getAdmissionStatsByStatus();
        Map<String, Long> statusMap = statusStats.stream()
                .collect(Collectors.toMap(
                        arr -> (String) arr[0],
                        arr -> (Long) arr[1]
                ));
        stats.put("byStatus", statusMap);

        // Year-wise count
        List<Object[]> yearStats = admissionRepository.getAdmissionStatsByYear();
        Map<String, Long> yearMap = yearStats.stream()
                .collect(Collectors.toMap(
                        arr -> (String) arr[0],
                        arr -> (Long) arr[1]
                ));
        stats.put("byYear", yearMap);

        // Total count
        long total = admissionRepository.count();
        stats.put("total", total);

        log.info("Generated statistics: {}", stats);
        return stats;
    }

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
        log.info("📌 MODE: Import ALL data - Handle missing enquiries");

        int successCount = 0;
        int withWarnings = 0;
        List<BulkImportResponseDTO.ImportError> errors = new ArrayList<>();

        for (int i = 0; i < dtos.size(); i++) {
            final int rowNumber = i + 2; // CSV row (header = 1)
            AdmissionRequestDTO dto = dtos.get(i);
            boolean hasWarnings = false;

            try {
                // ============ STEP 1: FIX MISSING/INVALID MOBILE ============
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

                // ============ STEP 2: CHECK FOR ENQUIRY (OPTIONAL) ============
                Long enquiryId = null;
                Optional<Enquiry> enquiryOpt = enquiryRepository
                        .findByMobileAndIsDeletedFalse(dto.getMobilePrimary());

                if (enquiryOpt.isPresent()) {
                    enquiryId = enquiryOpt.get().getId();
                    log.debug("✓ Row {}: Found enquiry ID: {}", rowNumber, enquiryId);
                } else {
                    // NO ENQUIRY - Create admission without enquiry link
                    hasWarnings = true;
                    log.warn("⚠️ Row {}: No enquiry found for mobile '{}' - Creating admission without enquiry link",
                            rowNumber, dto.getMobilePrimary());

                    errors.add(BulkImportResponseDTO.ImportError.builder()
                            .rowNumber(rowNumber)
                            .fieldName("enquiry")
                            .errorMessage("No enquiry found - admission created independently")
                            .rejectedValue(dto.getMobilePrimary())
                            .build());
                }

                // ============ STEP 3: HANDLE DUPLICATE MOBILE ============
                if (admissionRepository.existsByMobilePrimaryAndIsDeletedFalse(dto.getMobilePrimary())) {
                    String duplicateMobile = dto.getMobilePrimary();
                    String uniqueMobile = duplicateMobile + "_ADM" + rowNumber;
                    dto.setMobilePrimary(uniqueMobile);

                    hasWarnings = true;
                    log.warn("⚠️ Row {}: Duplicate mobile '{}' → Using unique '{}'",
                            rowNumber, duplicateMobile, uniqueMobile);

                    errors.add(BulkImportResponseDTO.ImportError.builder()
                            .rowNumber(rowNumber)
                            .fieldName("mobile")
                            .errorMessage("Duplicate mobile - made unique")
                            .rejectedValue(duplicateMobile)
                            .build());
                }

                // ============ STEP 4: FIX MISSING COURSES ============
                if (dto.getCourses() == null || dto.getCourses().isEmpty()) {
                    dto.setCourses(List.of("Not Specified"));
                    hasWarnings = true;
                    log.warn("⚠️ Row {}: Missing courses → Added placeholder", rowNumber);
                }

                // ============ STEP 5: FIX MISSING NAME ============
                if ((dto.getFirstName() == null || dto.getFirstName().trim().isEmpty()) &&
                        (dto.getLastName() == null || dto.getLastName().trim().isEmpty())) {

                    dto.setFirstName("Unknown");
                    dto.setLastName("Student");
                    hasWarnings = true;
                    log.warn("⚠️ Row {}: Missing name → Using 'Unknown Student'", rowNumber);
                }

                // ============ STEP 6: SET DEFAULTS ============
                if (dto.getAdmissionDate() == null) {
                    dto.setAdmissionDate(LocalDate.now());
                }
                if (dto.getLeadSource() == null || dto.getLeadSource().trim().isEmpty()) {
                    dto.setLeadSource("CSV Import");
                }
                if (dto.getAcademicYear() == null || dto.getAcademicYear().trim().isEmpty()) {
                    dto.setAcademicYear(String.valueOf(java.time.Year.now().getValue()));
                }

                // ============ STEP 7: CREATE ADMISSION ============
                Admission admission = admissionMapper.toEntity(dto);

                // Set enquiry ID (can be null)
                admission.setEnquiryId(enquiryId);

                // Generate registration number
                admission.setRegistrationNumber(generateRegistrationNumber());

                // Set metadata
                admission.setCreatedBy("BULK_IMPORT");

                // Save to database
                admissionRepository.save(admission);

                successCount++;
                if (hasWarnings) {
                    withWarnings++;
                }

                log.info("✅ Row {}: Imported {} (Mobile: {}, Reg: {}, Enquiry: {})",
                        rowNumber,
                        hasWarnings ? "WITH WARNINGS" : "SUCCESSFULLY",
                        dto.getMobilePrimary(),
                        admission.getRegistrationNumber(),
                        enquiryId != null ? enquiryId : "NONE");

            } catch (Exception e) {
                log.error("❌ Row {}: FAILED to save - {}", rowNumber, e.getMessage(), e);

                errors.add(BulkImportResponseDTO.ImportError.builder()
                        .rowNumber(rowNumber)
                        .fieldName("database")
                        .errorMessage("Database save failed: " + e.getMessage())
                        .rejectedValue(dto != null ? dto.getMobilePrimary() : "unknown")
                        .build());
            }
        }

        int failedCount = dtos.size() - successCount;

        log.info("📊 ==================== ADMISSION IMPORT COMPLETE ====================");
        log.info("   Total Records: {}", dtos.size());
        log.info("   ✅ Successfully Imported: {}", successCount);
        log.info("   ⚠️  With Warnings: {}", withWarnings);
        log.info("   ❌ Failed: {}", failedCount);
        log.info("   Success Rate: {}%", (successCount * 100 / dtos.size()));
        log.info("====================================================================");

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