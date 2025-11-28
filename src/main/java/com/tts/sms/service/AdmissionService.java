package com.tts.sms.service;

import com.tts.sms.dto.*;
import com.tts.sms.exception.ResourceNotFoundException;
import com.tts.sms.model.Admission;
import com.tts.sms.model.Enquiry;
import com.tts.sms.model.FeeInstallment;
import com.tts.sms.model.Fees;
import com.tts.sms.repository.*;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdmissionService {

    private final AdmissionRepository admissionRepository;
    private final EnquiryRepository enquiryRepository;
    private final FeeInstallmentRepository feeInstallmentRepository;
    private final FeesRepository feesRepository;
    private final AdmissionMapper admissionMapper;
    private final CSVService csvService;

    private static final AtomicInteger registrationCounter = new AtomicInteger(8000);
    private static final String REGISTRATION_PREFIX = "REG";
    private static volatile boolean counterInitialized = false;

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

        // Generate registration number if not provided
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

        // ✅ AUTOMATICALLY CREATE FEES RECORD
        createFeesRecord(savedAdmission);

        // Update enquiry status if exists
        if (enquiryId != null) {
            Enquiry enquiry = enquiryOpt.get();
            enquiry.setStatus("Admitted");
            enquiryRepository.save(enquiry);
        }

        // Generate fee installments if config provided
        if (requestDTO.getInstallmentConfig() != null) {
            generateInstallments(
                    savedAdmission.getRegistrationNumber(),
                    requestDTO.getInstallmentConfig(),
                    requestDTO.getTotalReceivableFees()
            );
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

        // ✅ UPDATE FEES RECORD
        updateFeesRecord(updated);

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

        // ✅ UPDATE FEES RECORD
        updateFeesRecord(transferred);

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

        // ✅ SOFT DELETE FEES RECORD
        feesRepository.findByRegistrationNumberAndIsDeletedFalse(admission.getRegistrationNumber())
                .ifPresent(fees -> {
                    fees.setIsDeleted(true);
                    fees.setDeletedAt(java.time.LocalDateTime.now());
                    feesRepository.save(fees);
                });

        log.info("Soft deleted admission with id: {}", id);
    }

    @Transactional
    public List<FeeInstallmentDTO> generateInstallments(String registrationNumber,
                                                        InstallmentConfigDTO config,
                                                        Double totalAmount) {
        log.debug("Generating {} installments for regNo: {}",
                config.getNumberOfInstallments(), registrationNumber);

        // Delete existing installments
        feeInstallmentRepository.deleteByRegistrationNumber(registrationNumber);

        List<FeeInstallment> installments = new ArrayList<>();
        Double amountPerInstallment = totalAmount / config.getNumberOfInstallments();
        LocalDate currentDate = config.getStartDate();

        for (int i = 1; i <= config.getNumberOfInstallments(); i++) {
            FeeInstallment installment = FeeInstallment.builder()
                    .registrationNumber(registrationNumber)
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
        log.info("Generated {} installments for regNo: {}", saved.size(), registrationNumber);

        return saved.stream()
                .map(admissionMapper::toInstallmentDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<FeeInstallmentDTO> getInstallments(Long admissionId) {
        log.debug("Fetching installments for admission: {}", admissionId);

        Admission admission = admissionRepository.findById(admissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Admission not found: " + admissionId));

        List<FeeInstallment> installments =
                feeInstallmentRepository.findByRegistrationNumberOrderByDueDateAsc(
                        admission.getRegistrationNumber()
                );

        return installments.stream()
                .map(admissionMapper::toInstallmentDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public boolean canCreateAdmission(String mobileNumber) {
        log.debug("Checking if admission can be created for mobile: {}", mobileNumber);

        List<Enquiry> enquiries = enquiryRepository.findAllByMobileAndIsDeletedFalse(mobileNumber);

        if (enquiries.isEmpty()) {
            log.warn("⚠️ No enquiry found for mobile: {} - Will create without pre-fill", mobileNumber);
        } else {
            log.info("✅ Found {} enquiry(ies) for mobile: {}", enquiries.size(), mobileNumber);
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

    // ==================== HELPER METHODS ====================

    /**
     * ✅ Create Fees record when admission is created
     */
    private void createFeesRecord(Admission admission) {
        try {
            String courseName = (admission.getCourses() != null && !admission.getCourses().isEmpty())
                    ? String.join(", ", admission.getCourses())
                    : "N/A";

            Fees fees = Fees.builder()
                    .registrationNumber(admission.getRegistrationNumber())
                    .admissionId(admission.getId())
                    .studentName(admission.getFullName())
                    .mobile(admission.getMobilePrimary())
                    .totalFees(admission.getTotalReceivableFees() != null ? admission.getTotalReceivableFees() : 0.0)
                    .feesDue(admission.getTotalReceivableFees() != null ? admission.getTotalReceivableFees() : 0.0)
                    .totalPaid(0.0)
                    .dueDate(null) // Will be set when installments are created
                    .feesRefund(0.0)
                    .status("Pending")
                    .course(courseName)
                    .createdBy("SYSTEM")
                    .build();

            feesRepository.save(fees);
            log.info("✅ Created fees record for regNo: {}", admission.getRegistrationNumber());

        } catch (Exception e) {
            log.error("❌ Failed to create fees record for regNo: {}",
                    admission.getRegistrationNumber(), e);
        }
    }

    /**
     * ✅ Update Fees record when admission is updated
     */
    private void updateFeesRecord(Admission admission) {
        try {
            feesRepository.findByRegistrationNumberAndIsDeletedFalse(admission.getRegistrationNumber())
                    .ifPresent(fees -> {
                        String courseName = (admission.getCourses() != null && !admission.getCourses().isEmpty())
                                ? String.join(", ", admission.getCourses())
                                : "N/A";

                        fees.setStudentName(admission.getFullName());
                        fees.setMobile(admission.getMobilePrimary());
                        fees.setTotalFees(admission.getTotalReceivableFees() != null ? admission.getTotalReceivableFees() : 0.0);
                        fees.setCourse(courseName);
                        fees.setUpdatedBy("SYSTEM");

                        // Recalculate fees due
                        Double feesDue = fees.getTotalFees() - fees.getTotalPaid() + fees.getFeesRefund();
                        fees.setFeesDue(feesDue);

                        feesRepository.save(fees);
                        log.info("✅ Updated fees record for regNo: {}", admission.getRegistrationNumber());
                    });

        } catch (Exception e) {
            log.error("❌ Failed to update fees record for regNo: {}",
                    admission.getRegistrationNumber(), e);
        }
    }

    private String generateRegistrationNumber() {
        // ✅ Initialize counter from database only once
        if (!counterInitialized) {
            synchronized (AdmissionService.class) {
                if (!counterInitialized) {
                    initializeRegistrationCounter();
                    counterInitialized = true;
                }
            }
        }

        // ✅ Generate next number atomically
        int nextNumber = registrationCounter.getAndIncrement();
        return String.format("%s%04d", REGISTRATION_PREFIX, nextNumber);
    }

    /**
     * ✅ Initialize counter from database - finds max and starts from 8000 or max+1
     */
    private void initializeRegistrationCounter() {
        try {
            String maxRegNo = admissionRepository.findMaxRegistrationNumber(REGISTRATION_PREFIX);

            if (maxRegNo != null && maxRegNo.length() > REGISTRATION_PREFIX.length()) {
                try {
                    String numberPart = maxRegNo.substring(REGISTRATION_PREFIX.length());
                    int maxNumber = Integer.parseInt(numberPart);

                    // ✅ Start from max+1 or 8000, whichever is greater
                    int startNumber = Math.max(maxNumber + 1, 8000);
                    registrationCounter.set(startNumber);

                    log.info("✅ Registration counter initialized to: {}", startNumber);
                } catch (NumberFormatException e) {
                    log.warn("Error parsing registration number: {}, starting from 8000", maxRegNo);
                    registrationCounter.set(8000);
                }
            } else {
                // ✅ No existing records, start from 8000
                registrationCounter.set(8000);
                log.info("✅ No existing registrations, starting from 8000");
            }
        } catch (Exception e) {
            log.error("Error initializing registration counter, defaulting to 8000", e);
            registrationCounter.set(8000);
        }
    }

    private AdmissionResponseDTO toResponseDTOWithInstallments(Admission admission) {
        AdmissionResponseDTO dto = admissionMapper.toResponseDTO(admission);

        List<FeeInstallment> installments =
                feeInstallmentRepository.findByRegistrationNumberOrderByDueDateAsc(
                        admission.getRegistrationNumber()
                );

        dto.setInstallments(installments.stream()
                .map(admissionMapper::toInstallmentDTO)
                .collect(Collectors.toList()));

        dto.setTotalInstallments(installments.size());

        Double totalPaid = feeInstallmentRepository.getTotalPaidAmount(admission.getRegistrationNumber());
        Double totalDue = feeInstallmentRepository.getTotalDueAmount(admission.getRegistrationNumber());

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

    // ==================== BULK IMPORT ====================

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
        log.info("🔄 EXACT IMPORT: Processing {} records", dtos.size());

        int successCount = 0;
        int withWarnings = 0;
        List<BulkImportResponseDTO.ImportError> errors = new ArrayList<>();

        for (int i = 0; i < dtos.size(); i++) {
            final int rowNumber = i + 2;
            AdmissionRequestDTO dto = dtos.get(i);
            boolean hasWarnings = false;

            try {
                if (dto.getMobilePrimary() == null || dto.getMobilePrimary().trim().isEmpty()) {
                    dto.setMobilePrimary("N/A");
                    hasWarnings = true;
                }

                if (dto.getFirstName() == null || dto.getFirstName().trim().isEmpty()) {
                    dto.setFirstName("N/A");
                    hasWarnings = true;
                }

                if (dto.getLastName() == null || dto.getLastName().trim().isEmpty()) {
                    dto.setLastName("N/A");
                    hasWarnings = true;
                }

                Long enquiryId = null;
                try {
                    List<Enquiry> enquiries = enquiryRepository
                            .findAllByMobileAndIsDeletedFalse(dto.getMobilePrimary());

                    if (!enquiries.isEmpty()) {
                        enquiryId = enquiries.get(0).getId();
                    }
                } catch (Exception e) {
                    log.debug("Row {}: Enquiry check skipped", rowNumber);
                }

                if (dto.getCourses() == null || dto.getCourses().isEmpty()) {
                    dto.setCourses(List.of("N/A"));
                    hasWarnings = true;
                }

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
                    dto.setDocumentType("N/A");
                }

                Admission admission = admissionMapper.toEntity(dto);
                admission.setEnquiryId(enquiryId);
                admission.setCreatedBy("BULK_IMPORT");

                String regNumber = dto.getRegistrationNumber();
                if (regNumber != null && !regNumber.trim().isEmpty()) {
                    Admission existing = admissionRepository
                            .findByRegistrationNumberAndIsDeletedFalse(regNumber);

                    if (existing != null) {
                        regNumber = regNumber + "_DUP" + rowNumber;
                        hasWarnings = true;

                        errors.add(BulkImportResponseDTO.ImportError.builder()
                                .rowNumber(rowNumber)
                                .fieldName("registrationNumber")
                                .errorMessage("Duplicate reg number - appended suffix")
                                .rejectedValue(dto.getRegistrationNumber())
                                .build());
                    }
                    admission.setRegistrationNumber(regNumber);
                } else {
                    admission.setRegistrationNumber(generateRegistrationNumber());
                }

                try {
                    saveAdmissionInNewTransaction(admission);
                    successCount++;

                    if (hasWarnings) {
                        withWarnings++;
                    }

                    log.info("✅ Row {}: Saved (Mobile: {}, Reg: {})",
                            rowNumber, admission.getMobilePrimary(), admission.getRegistrationNumber());

                } catch (Exception saveEx) {
                    log.error("❌ Row {}: Save failed - {}", rowNumber, saveEx.getMessage());

                    errors.add(BulkImportResponseDTO.ImportError.builder()
                            .rowNumber(rowNumber)
                            .fieldName("database")
                            .errorMessage("Save failed: " + saveEx.getMessage())
                            .rejectedValue(dto.getMobilePrimary())
                            .build());
                }

            } catch (Exception e) {
                log.error("❌ Row {}: Processing error - {}", rowNumber, e.getMessage(), e);

                errors.add(BulkImportResponseDTO.ImportError.builder()
                        .rowNumber(rowNumber)
                        .fieldName("processing")
                        .errorMessage("Error: " + e.getMessage())
                        .rejectedValue(dto != null ? dto.getMobilePrimary() : "unknown")
                        .build());
            }
        }

        int failedCount = dtos.size() - successCount;

        log.info("📊 IMPORT RESULT: {}/{} saved ({} warnings, {} failed)",
                successCount, dtos.size(), withWarnings, failedCount);

        return BulkImportResponseDTO.builder()
                .success(successCount > 0)
                .totalRecords(dtos.size())
                .successfulImports(successCount)
                .failedImports(failedCount)
                .errors(errors)
                .message(String.format(
                        "%d/%d records saved (%d warnings, %d failed)",
                        successCount, dtos.size(), withWarnings, failedCount
                ))
                .build();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveAdmissionInNewTransaction(Admission admission) {
        Admission saved = admissionRepository.save(admission);
        // ✅ Create fees record immediately
        createFeesRecord(saved);
    }
}