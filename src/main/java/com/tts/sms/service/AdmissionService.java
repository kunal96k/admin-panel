package com.tts.sms.service;

import com.tts.sms.dto.*;
import com.tts.sms.exception.ResourceNotFoundException;
import com.tts.sms.model.Admission;
import com.tts.sms.model.Certificate;
import com.tts.sms.model.Course;
import com.tts.sms.model.Enquiry;
import com.tts.sms.model.FeeInstallment;
import com.tts.sms.model.Fees;
import com.tts.sms.model.Package;
import com.tts.sms.repository.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private final PackageRepository packageRepository;
    private final CourseRepository courseRepository;
    private final AdmissionMapper admissionMapper;
    private final CSVService csvService;
    private final StudentCategoryService studentCategoryService;
    private final SystemConfigurationService systemConfigurationService;
    private final FileStorageService fileStorageService;
    private final BatchRepository batchRepository;
    private final BatchService batchService;
    private final CertificateRepository certificateRepository;

    private static final AtomicInteger registrationCounter = new AtomicInteger(8000);
    private static final String REGISTRATION_PREFIX = "REG";
    private static volatile boolean counterInitialized = false;

    /**
     * Get all admissions with fees data for export
     */
    public List<AdmissionExportDTO> getAllAdmissionsForExport() {
        log.info("Fetching all admissions for export with fees data");

        List<Admission> admissions = admissionRepository.findAllByOrderByCreatedAtDesc();

        if (admissions.isEmpty()) {
            return Collections.emptyList();
        }

        // Batch fetch all fees data to avoid N+1 database load (database crease issue)
        List<String> regNos = admissions.stream()
                .map(Admission::getRegistrationNumber)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Map<String, Fees> feesMap = feesRepository.findAllByRegistrationNumberInAndIsDeletedFalse(regNos)
                .stream()
                .collect(Collectors.toMap(Fees::getRegistrationNumber, f -> f, (existing, replacement) -> existing));

        return admissions.stream()
                .map(a -> mapToExportDTOOptimized(a, feesMap.get(a.getRegistrationNumber())))
                .collect(Collectors.toList());
    }

    private AdmissionExportDTO mapToExportDTOOptimized(Admission admission, Fees fees) {
        String totalFeesStr = "-";
        String receivableStr = "-";

        if (fees != null) {
            totalFeesStr = fees.getTotalFees() != null && fees.getTotalFees() > 0
                    ? "₹" + String.format("%.2f", fees.getTotalFees())
                    : "-";

            receivableStr = fees.getFeesDue() != null && fees.getFeesDue() > 0
                    ? "₹" + String.format("%.2f", fees.getFeesDue())
                    : "-";
        } else {
            if (admission.getTotalPayableFees() != null && admission.getTotalPayableFees() > 0) {
                totalFeesStr = "₹" + String.format("%.2f", admission.getTotalPayableFees());
            }

            if (admission.getTotalReceivableFees() != null && admission.getTotalReceivableFees() > 0) {
                receivableStr = "₹" + String.format("%.2f", admission.getTotalReceivableFees());
            }
        }

        String studentName = buildFullName(
                admission.getFirstName(),
                admission.getMiddleName(),
                admission.getLastName());

        return AdmissionExportDTO.builder()
                .registrationNumber(admission.getRegistrationNumber() != null ? admission.getRegistrationNumber() : "-")
                .studentName(studentName)
                .mobile(admission.getMobilePrimary() != null ? admission.getMobilePrimary() : "-")
                .email(admission.getEmailPrimary() != null ? admission.getEmailPrimary() : "-")
                .courses(admission.getCourses() != null && !admission.getCourses().isEmpty()
                        ? String.join(", ", admission.getCourses())
                        : "-")
                .college(admission.getCollege() != null ? admission.getCollege() : "-")
                .totalFees(totalFeesStr)
                .receivableFees(receivableStr)
                .admissionDate(admission.getAdmissionDate() != null ? admission.getAdmissionDate().toString() : "-")
                .studentCategory(admission.getStudentCategory() != null ? admission.getStudentCategory() : "-")
                .build();
    }

    /**
     * Update student category based on current rules
     */
    @Transactional
    public void updateStudentCategory(Long admissionId) {
        Admission admission = admissionRepository.findById(admissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Admission not found"));

        LocalDate cutoffDate = systemConfigurationService.getCutoffDate();
        String newCategory = studentCategoryService.determineCategory(admission, cutoffDate);

        admission.setStudentCategory(newCategory);
        admission.setCategoryUpdatedAt(LocalDateTime.now());

        admissionRepository.save(admission);
        log.info(" Updated category for {} to: {}", admission.getRegistrationNumber(), newCategory);

        // SYNC WITH FEES MANAGER
        updateFeesRecord(admission);
    }

    /**
     * For admins to override category
     */
    @Transactional
    public AdmissionResponseDTO updateStudentCategory(Long admissionId, String newCategory) {
        log.info("🔧 Manual category change for admission: {} to {}", admissionId, newCategory);

        Admission admission = admissionRepository.findById(admissionId)
                .filter(a -> !a.getIsDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Admission not found: " + admissionId));

        // Validate category
        List<String> validCategories = List.of("OLD_STUDENT", "NEW_STUDENT", "PURSUING", "COMPLETED", "CANCELLED");
        if (!validCategories.contains(newCategory)) {
            throw new IllegalArgumentException("Invalid category: " + newCategory);
        }

        // Update category
        admission.setStudentCategory(newCategory);
        admission.setCategoryUpdatedAt(LocalDateTime.now());
        admission.setUpdatedBy("MANUAL_OVERRIDE");

        Admission updated = admissionRepository.save(admission);
        log.info(" Category updated: {} -> {}", admission.getRegistrationNumber(), newCategory);

        // SYNC WITH FEES MANAGER
        updateFeesRecord(updated);

        return toResponseDTOWithInstallments(updated);
    }

    // Helper method to build full name
    private String buildFullName(String firstName, String middleName, String lastName) {
        StringBuilder name = new StringBuilder();

        if (firstName != null && !firstName.trim().isEmpty()) {
            name.append(firstName.trim());
        }

        if (middleName != null && !middleName.trim().isEmpty()) {
            if (name.length() > 0)
                name.append(" ");
            name.append(middleName.trim());
        }

        if (lastName != null && !lastName.trim().isEmpty()) {
            if (name.length() > 0)
                name.append(" ");
            name.append(lastName.trim());
        }

        return name.length() > 0 ? name.toString() : "-";
    }

    @Transactional(readOnly = true)
    public Page<AdmissionResponseDTO> getAllAdmissions(int page, int size) {
        log.debug("Fetching admissions - page: {}, size: {}", page, size);

        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "admission_date", "created_at"));

        Page<Admission> admissions = admissionRepository.findByIsDeletedFalse(pageable);

        log.info("Retrieved {} admissions out of {} total",
                admissions.getNumberOfElements(), admissions.getTotalElements());

        return mapToResponseDTOBatch(admissions);
    }

    @Transactional(readOnly = true)
    public AdmissionResponseDTO getByRegistrationNumber(String regNo) {
        Admission admission = admissionRepository.findByRegistrationNumberAndIsDeletedFalse(regNo);

        if (admission == null) {
            throw new ResourceNotFoundException("Admission not found: " + regNo);
        }

        return admissionMapper.toResponseDTO(admission);
    }

    @Transactional(readOnly = true)
    public AdmissionCursorResponseDTO searchAdmissionsCursor(AdmissionSearchDTO searchDTO) {
        log.debug("Searching admissions with cursor: {}", searchDTO);

        int limit = searchDTO.getSize() != null ? searchDTO.getSize() : 25;

        // Fetch one extra to determine if there's more
        List<Admission> admissions = admissionRepository.searchWithCursor(
                searchDTO.getLastId(),
                searchDTO.getSearchTerm(),
                searchDTO.getStatus(),
                searchDTO.getCourse(),
                searchDTO.getBatch(),
                searchDTO.getAcademicYear(),
                searchDTO.getAdmissionDateFrom(),
                searchDTO.getAdmissionDateTo(),
                limit + 1,
                searchDTO.getStudentCategory());

        boolean hasMore = admissions.size() > limit;
        if (hasMore) {
            admissions = admissions.subList(0, limit);
        }

        Long nextCursor = null;
        if (!admissions.isEmpty()) {
            nextCursor = admissions.get(admissions.size() - 1).getId();
        }

        long totalElements = admissionRepository.countSearch(
                searchDTO.getSearchTerm(),
                searchDTO.getStatus(),
                searchDTO.getCourse(),
                searchDTO.getBatch(),
                searchDTO.getAcademicYear(),
                searchDTO.getAdmissionDateFrom(),
                searchDTO.getAdmissionDateTo(),
                searchDTO.getStudentCategory());

        List<AdmissionResponseDTO> dtos = mapToResponseDTOBatch(admissions);

        return AdmissionCursorResponseDTO.builder()
                .content(dtos)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .totalElements(totalElements)
                .build();
    }

    @Transactional(readOnly = true)
    public Page<AdmissionResponseDTO> searchAdmissions(AdmissionSearchDTO searchDTO) {
        log.debug("Searching admissions with criteria: {}", searchDTO);

        Sort sort = Sort.by(
                "DESC".equalsIgnoreCase(searchDTO.getSortDirection())
                        ? Sort.Direction.DESC
                        : Sort.Direction.ASC,
                convertToSnakeCase(searchDTO.getSortBy()));

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
                searchDTO.getStudentCategory(), searchDTO.getStudentCategory(),
                pageable);

        log.info("Search returned {} results", results.getTotalElements());
        return mapToResponseDTOBatch(results);
    }

    /**
     * Optimized batch mapping from Admission to AdmissionResponseDTO
     * Eliminates N+1 problem by batch fetching associated Fee data
     */
    private Page<AdmissionResponseDTO> mapToResponseDTOBatch(Page<Admission> admissionPage) {
        List<AdmissionResponseDTO> dtos = mapToResponseDTOBatch(admissionPage.getContent());
        return new PageImpl<>(dtos, admissionPage.getPageable(), admissionPage.getTotalElements());
    }

    private List<AdmissionResponseDTO> mapToResponseDTOBatch(List<Admission> admissions) {
        if (admissions.isEmpty()) {
            return Collections.emptyList();
        }

        // Get all registration numbers for batch fetching
        List<String> regNos = admissions.stream()
                .map(Admission::getRegistrationNumber)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // Batch fetch fees summary data
        Map<String, Fees> feesMap = feesRepository.findAllByRegistrationNumberInAndIsDeletedFalse(regNos)
                .stream()
                .collect(Collectors.toMap(Fees::getRegistrationNumber, f -> f, (existing, replacement) -> existing));

        return admissions.stream()
                .map(admission -> {
                    AdmissionResponseDTO dto = admissionMapper.toResponseDTO(admission);

                    // Use batch-fetched fees data instead of hitting repository for each row
                    Fees fees = feesMap.get(admission.getRegistrationNumber());
                    if (fees != null) {
                        dto.setTotalPaidAmount(fees.getTotalPaid() != null ? fees.getTotalPaid() : 0.0);
                        dto.setTotalDueAmount(fees.getFeesDue() != null ? fees.getFeesDue() : 0.0);
                        dto.setTotalInstallmentAmount(fees.getTotalInstallmentAmount());
                        dto.setNumberOfInstallments(fees.getNumberOfInstallments());
                        
                        String feesStatus = fees.getStatus() != null ? fees.getStatus() : "Pending";
                        
                        // Override with Cancelled if student category is CANCELLED
                        if ("CANCELLED".equalsIgnoreCase(admission.getStudentCategory())) {
                            feesStatus = "Cancelled";
                        } 
                        // Dynamically check for Overdue if not Clear or Cancelled
                        else if (!"Clear".equalsIgnoreCase(feesStatus) && fees.getDueDate() != null && fees.getDueDate().isBefore(LocalDate.now())) {
                            feesStatus = "Overdue";
                        }
                        
                        dto.setFeesStatus(feesStatus);
                    } else {
                        dto.setTotalPaidAmount(0.0);
                        dto.setTotalDueAmount(0.0);
                        dto.setFeesStatus("Pending");
                    }

                    // Note: full installments list is still skipped for list view to maximize
                    // performance
                    // If installments are needed, they should be fetched on-demand or in a separate
                    // batch
                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * Convert camelCase field names to snake_case for database queries
     */
    private String convertToSnakeCase(String fieldName) {
        if (fieldName == null)
            return "created_at";

        // Map common fields
        Map<String, String> fieldMapping = Map.of(
                "createdAt", "created_at",
                "updatedAt", "updated_at",
                "admissionDate", "admission_date",
                "registrationNumber", "registration_number",
                "mobilePrimary", "mobile_primary");

        return fieldMapping.getOrDefault(fieldName,
                fieldName.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase());
    }

    @Transactional
    public AdmissionResponseDTO createAdmission(AdmissionRequestDTO requestDTO) {
        log.debug("Creating new admission for mobile: {}", requestDTO.getMobilePrimary());

        // Find enquiry
        Long enquiryId;
        List<Enquiry> enquiries = enquiryRepository
                .findAllByMobileAndIsDeletedFalse(requestDTO.getMobilePrimary());

        if (!enquiries.isEmpty()) {
            Enquiry latestEnquiry = enquiries.stream()
                    .max(Comparator.comparing(e -> e.getEnquiryDate() != null
                            ? e.getEnquiryDate()
                            : LocalDate.MIN))
                    .orElse(enquiries.get(0));

            enquiryId = latestEnquiry.getId();

            if (enquiries.size() > 1) {
                log.info(" Found {} enquiries for mobile: {}, using latest (ID: {})",
                        enquiries.size(), requestDTO.getMobilePrimary(), enquiryId);
            } else {
                log.info(" Found 1 enquiry with ID: {} for mobile: {}",
                        enquiryId, requestDTO.getMobilePrimary());
            }
        } else {
            enquiryId = null;
            log.warn(" No enquiry found for mobile: {} - Creating admission without enquiry link",
                    requestDTO.getMobilePrimary());
        }

        // Create admission entity
        Admission admission = admissionMapper.toEntity(requestDTO);
        admission.setEnquiryId(enquiryId);

        boolean isNewAdmission = false;
        String importSource = "NEW_ENTRY";

        // Generate registration number if not provided
        if (requestDTO.getRegistrationNumber() != null && !requestDTO.getRegistrationNumber().trim().isEmpty()) {
            admission.setRegistrationNumber(requestDTO.getRegistrationNumber());
            isNewAdmission = requestDTO.getRegistrationNumber().startsWith("REG");
            importSource = isNewAdmission ? "NEW_ENTRY" : "IMPORTED_OLD_DATA";
        } else {
            admission.setRegistrationNumber(generateRegistrationNumber());
            isNewAdmission = true;
            importSource = "NEW_ENTRY";
        }

        admission.setImportSource(importSource);
        admission.setCreatedBy("SYSTEM");

        // SET CATEGORY BEFORE FIRST SAVE
        LocalDate cutoffDate = systemConfigurationService.getCutoffDate();
        String category = studentCategoryService.determineCategory(admission, cutoffDate);
        admission.setStudentCategory(category);
        admission.setCategoryUpdatedAt(LocalDateTime.now());

        // Handle photo upload
        if (requestDTO.getStudentPhoto() != null && !requestDTO.getStudentPhoto().trim().isEmpty()) {
            try {
                String photoPath = fileStorageService.saveBase64Image(requestDTO.getStudentPhoto(), "admissions");
                admission.setPhotoPath(photoPath);
                log.info(" Saved student photo for {}", admission.getRegistrationNumber());
            } catch (Exception e) {
                log.error(" Failed to save student photo: {}", e.getMessage());
            }
        }

        // Save admission
        Admission savedAdmission = admissionRepository.save(admission);
        log.info(" Created admission with id: {} and reg no: {} - Category: {}",
                savedAdmission.getId(), savedAdmission.getRegistrationNumber(), category);

        // Update enquiry status if exists
        if (enquiryId != null) {
            Enquiry enquiry = enquiries.stream()
                    .filter(e -> e.getId().equals(enquiryId))
                    .findFirst()
                    .orElse(null);

            if (enquiry != null) {
                enquiry.setStatus("Admitted");
                enquiryRepository.save(enquiry);
                log.info(" Updated enquiry {} status to 'Admitted'", enquiryId);
            }
        }

        // ONLY create fees record for NEW admissions (REG* numbers)
        if (isNewAdmission) {
            log.info(" NEW ADMISSION: Creating fees record for regNo: {}",
                    savedAdmission.getRegistrationNumber());
            createFeesRecord(savedAdmission);
        } else {
            log.info(" OLD CSV IMPORT: Skipping fees record creation for regNo: {}",
                    savedAdmission.getRegistrationNumber());
        }

        // Generate fee installments if config provided (only for new admissions)
        if (isNewAdmission && requestDTO.getInstallmentConfig() != null) {
            generateInstallments(
                    savedAdmission.getRegistrationNumber(),
                    requestDTO.getInstallmentConfig(),
                    requestDTO.getTotalReceivableFees());
        }

        return toResponseDTOWithInstallments(savedAdmission);
    }

    /**
     * Create fees record ONLY for NEW admissions (REG* numbers)
     * This method is NEVER called for old CSV imports
     */
    private void createFeesRecord(Admission admission) {
        try {
            // Double-check: Only proceed if registration number starts with REG
            if (!admission.getRegistrationNumber().startsWith("REG")) {
                log.warn(" Skipping fees record - Not a REG number: {}",
                        admission.getRegistrationNumber());
                return;
            }

            // Check if already exists
            Optional<Fees> existingFees = feesRepository
                    .findByRegistrationNumberAndIsDeletedFalse(admission.getRegistrationNumber());

            if (existingFees.isPresent()) {
                log.info("Fees record already exists for regNo: {} - Skipping",
                        admission.getRegistrationNumber());
                return;
            }

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
                    .dueDate(null)
                    .feesRefund(0.0)
                    .status("Pending")
                    .course(courseName)
                    .installmentStartDate(null)
                    .numberOfInstallments(null)
                    .daysBetweenInstallments(null)
                    .totalInstallmentAmount(null)
                    .createdBy("SYSTEM")
                    .build();

            Fees savedFees = feesRepository.save(fees);
            feesRepository.flush();

            log.info(" Created and flushed fees record for regNo: {}", admission.getRegistrationNumber());

        } catch (Exception e) {
            log.error(" Failed to create fees record for regNo: {}",
                    admission.getRegistrationNumber(), e);
            throw new RuntimeException("Failed to create fees record: " + e.getMessage(), e);
        }
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
        
        // Handle photo update
        if (requestDTO.getStudentPhoto() != null && !requestDTO.getStudentPhoto().trim().isEmpty()) {
            try {
                String photoPath = fileStorageService.saveBase64Image(requestDTO.getStudentPhoto(), "admissions");
                existingAdmission.setPhotoPath(photoPath);
                log.info(" Updated student photo for {}", existingAdmission.getRegistrationNumber());
            } catch (Exception e) {
                log.error(" Failed to update student photo: {}", e.getMessage());
            }
        }

        existingAdmission.setUpdatedBy("SYSTEM");

        Admission updated = admissionRepository.save(existingAdmission);
        log.info("Updated admission with id: {}", id);

        // SYNC WITH FEES MANAGER
        updateFeesRecord(updated);

        // SYNC WITH CERTIFICATES
        syncCertificatesWithAdmission(updated);

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
                transferDTO.getTransferReason() != null ? transferDTO.getTransferReason() : "N/A");
        admission.setNotes(
                (admission.getNotes() != null ? admission.getNotes() : "") + transferNote);

        admission.setUpdatedBy("SYSTEM");

        Admission transferred = admissionRepository.save(admission);
        log.info("Transferred admission with id: {} to academic year: {}",
                admission.getId(), transferDTO.getAcademicYear());

        // UPDATE FEES RECORD
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

        // SOFT DELETE FEES RECORD
        feesRepository.findByRegistrationNumberAndIsDeletedFalse(admission.getRegistrationNumber())
                .ifPresent(fees -> {
                    fees.setIsDeleted(true);
                    fees.setDeletedAt(java.time.LocalDateTime.now());
                    feesRepository.save(fees);
                });

        log.info("Soft deleted admission with id: {}", id);
    }

    @Transactional(readOnly = true)
    public List<FeeInstallmentDTO> getInstallments(Long admissionId) {
        log.debug("Fetching installments for admission: {}", admissionId);

        Admission admission = admissionRepository.findById(admissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Admission not found: " + admissionId));

        List<FeeInstallment> installments = feeInstallmentRepository.findByRegistrationNumberOrderByDueDateAsc(
                admission.getRegistrationNumber());

        return installments.stream()
                .map(admissionMapper::toInstallmentDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public boolean canCreateAdmission(String mobileNumber) {
        log.debug("Checking if admission can be created for mobile: {}", mobileNumber);

        List<Enquiry> enquiries = enquiryRepository.findAllByMobileAndIsDeletedFalse(mobileNumber);

        if (enquiries.isEmpty()) {
            log.warn(" No enquiry found for mobile: {} - Will create without pre-fill", mobileNumber);
        } else {
            log.info(" Found {} enquiry(ies) for mobile: {}", enquiries.size(), mobileNumber);
        }

        return true;
    }

    @Transactional(readOnly = true)
    public EnquiryResponseDTO getEnquiryForAdmission(String mobileNumber) {
        log.debug("Fetching enquiry data for mobile: {}", mobileNumber);

        List<Enquiry> enquiries = enquiryRepository
                .findAllByMobileAndIsDeletedFalse(mobileNumber);

        if (enquiries.isEmpty()) {
            throw new ResourceNotFoundException(
                    "No enquiry found for mobile: " + mobileNumber);
        }

        // Handle multiple enquiries - always use latest
        Enquiry latestEnquiry = enquiries.stream()
                .max(Comparator.comparing(e -> e.getEnquiryDate() != null
                        ? e.getEnquiryDate()
                        : LocalDate.MIN))
                .orElse(enquiries.get(0));

        if (enquiries.size() > 1) {
            log.warn(" Found {} enquiries for mobile: {}. Using latest from {}",
                    enquiries.size(), mobileNumber,
                    latestEnquiry.getEnquiryDate() != null
                            ? latestEnquiry.getEnquiryDate()
                            : "unknown date");
        } else {
            log.info(" Found 1 enquiry for mobile: {}", mobileNumber);
        }

        return EnquiryResponseDTO.builder()
                .id(latestEnquiry.getId())
                .firstName(latestEnquiry.getFirstName())
                .middleName(latestEnquiry.getMiddleName())
                .lastName(latestEnquiry.getLastName())
                .mobile(latestEnquiry.getMobile())
                .secondaryMobile(latestEnquiry.getSecondaryMobile())
                .email(latestEnquiry.getEmail())
                .currentAddress(latestEnquiry.getCurrentAddress())
                .college(latestEnquiry.getCollege())
                .qualification(latestEnquiry.getQualification())
                .aadhaar(latestEnquiry.getAadhaar())
                .birthDate(latestEnquiry.getBirthDate())
                .gender(latestEnquiry.getGender())
                .coursesList(latestEnquiry.getCourses())
                .source(latestEnquiry.getSource())
                .date(latestEnquiry.getEnquiryDate())
                .totalFees(calculateEnquiryFees(latestEnquiry))
                .build();
    }

    /**
     * Calculate estimated fees for an enquiry
     */
    private Double calculateEnquiryFees(Enquiry enquiry) {
        // 1. Check Package
        if (enquiry.getPackageName() != null && !enquiry.getPackageName().trim().isEmpty()) {
            Optional<Package> pkg = packageRepository
                    .findByPackageNameIgnoreCaseAndIsActiveTrue(enquiry.getPackageName());
            if (pkg.isPresent()) {
                return pkg.get().getTotalAmount().doubleValue();
            }
        }

        // 2. Sum up courses if no package match
        if (enquiry.getCourses() != null && !enquiry.getCourses().isEmpty()) {
            double total = 0.0;
            for (String courseName : enquiry.getCourses()) {
                Optional<Course> course = courseRepository
                        .findByCourseNameAndIsActiveTrue(courseName);
                if (course.isPresent()) {
                    total += course.get().getCourseFees().doubleValue();
                }
            }
            return total;
        }

        return 0.0;
    }

    // ==================== HELPER METHODS ====================

    /**
     * Update Fees record when admission is updated
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
                        fees.setTotalFees(
                                admission.getTotalReceivableFees() != null ? admission.getTotalReceivableFees() : 0.0);
                        fees.setCourse(courseName);
                        fees.setUpdatedBy("SYSTEM");

                        // Recalculate fees due
                        Double feesDue = fees.getTotalFees() - fees.getTotalPaid() + fees.getFeesRefund();
                        fees.setFeesDue(feesDue);

                        feesRepository.save(fees);
                        log.info(" Updated fees record for regNo: {}", admission.getRegistrationNumber());
                    });

        } catch (Exception e) {
            log.error(" Failed to update fees record for regNo: {}",
                    admission.getRegistrationNumber(), e);
        }
    }

    /**
     * Sync certificates when admission student name or courses change.
     * Updates studentName in all certificates for this registration number.
     */
    private void syncCertificatesWithAdmission(Admission admission) {
        try {
            List<Certificate> certificates = certificateRepository
                    .findByRegistrationNoAndIsActiveTrue(admission.getRegistrationNumber());

            if (certificates.isEmpty()) {
                return;
            }

            String newName = admission.getFullName();
            String newEmail = admission.getEmailPrimary();

            for (Certificate cert : certificates) {
                boolean changed = false;

                // Sync student name
                if (newName != null && !newName.equals(cert.getStudentName())) {
                    cert.setStudentName(newName);
                    changed = true;
                }

                // Sync email
                if (newEmail != null && !newEmail.equals(cert.getStudentEmail())) {
                    cert.setStudentEmail(newEmail);
                    changed = true;
                }

                if (changed) {
                    certificateRepository.save(cert);
                    log.info("✅ Synced certificate {} for regNo: {} — name: {}",
                            cert.getCertificateNo(), admission.getRegistrationNumber(), newName);
                }
            }
        } catch (Exception e) {
            log.error("❌ Failed to sync certificates for regNo: {}",
                    admission.getRegistrationNumber(), e);
        }
    }

    private String generateRegistrationNumber() {
        // Initialize counter from database only once
        if (!counterInitialized) {
            synchronized (AdmissionService.class) {
                if (!counterInitialized) {
                    initializeRegistrationCounter();
                    counterInitialized = true;
                }
            }
        }

        // Generate next number atomically
        int nextNumber = registrationCounter.getAndIncrement();
        return String.format("%s%04d", REGISTRATION_PREFIX, nextNumber);
    }

    /**
     * Initialize counter from database - finds max and starts from 8000 or max+1
     */
    private void initializeRegistrationCounter() {
        try {
            String maxRegNo = admissionRepository.findMaxRegistrationNumber(REGISTRATION_PREFIX);

            if (maxRegNo != null && maxRegNo.length() > REGISTRATION_PREFIX.length()) {
                try {
                    String numberPart = maxRegNo.substring(REGISTRATION_PREFIX.length());
                    int maxNumber = Integer.parseInt(numberPart);

                    // Start from max+1 or 8000, whichever is greater
                    int startNumber = Math.max(maxNumber + 1, 8000);
                    registrationCounter.set(startNumber);

                    log.info(" Registration counter initialized to: {}", startNumber);
                } catch (NumberFormatException e) {
                    log.warn("Error parsing registration number: {}, starting from 8000", maxRegNo);
                    registrationCounter.set(8000);
                }
            } else {
                // No existing records, start from 8000
                registrationCounter.set(8000);
                log.info(" No existing registrations, starting from 8000");
            }
        } catch (Exception e) {
            log.error("Error initializing registration counter, defaulting to 8000", e);
            registrationCounter.set(8000);
        }
    }

    private AdmissionResponseDTO toResponseDTOWithInstallments(Admission admission) {
        AdmissionResponseDTO dto = admissionMapper.toResponseDTO(admission);

        // Fetch and populate full batch details
        if (admission.getBatches() != null && !admission.getBatches().isEmpty()) {
            List<BatchResponseDTO> batchDetails = admission.getBatches().stream()
                    .map(rawValue -> {
                        if (rawValue == null || rawValue.trim().isEmpty()) {
                            return null;
                        }

                        String value = rawValue.trim();

                        // Common case: UI saves batchNo (like "1", "B001", etc.)
                        return batchRepository.findByBatchNo(value)
                                .map(batchService::convertToResponseDTO)
                                .orElseGet(() -> {
                                    // Fallback: sometimes stored as "Batch Name (09:00 - 10:00)" or similar
                                    String batchName = value;
                                    int index = value.indexOf(" (");
                                    if (index != -1) {
                                        batchName = value.substring(0, index).trim();
                                    }

                                    // Try by name
                                    List<com.tts.sms.model.Batch> byName = batchRepository.findByBatchName(batchName);
                                    if (!byName.isEmpty()) {
                                        return batchService.convertToResponseDTO(byName.get(0));
                                    }

                                    // Last fallback: if the stored value is numeric but actually refers to batchNo
                                    List<com.tts.sms.model.Batch> byValueAsName = batchRepository.findByBatchName(value);
                                    return byValueAsName.isEmpty() ? null : batchService.convertToResponseDTO(byValueAsName.get(0));
                                });
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            dto.setBatchDetails(batchDetails);
        }

        List<FeeInstallment> installments = feeInstallmentRepository.findByRegistrationNumberOrderByDueDateAsc(
                admission.getRegistrationNumber());

        dto.setInstallments(installments.stream()
                .map(admissionMapper::toInstallmentDTO)
                .collect(Collectors.toList()));

        dto.setTotalInstallments(installments.size());

        Double totalPaid = feeInstallmentRepository.getTotalPaidAmount(admission.getRegistrationNumber());
        Double totalDue = feeInstallmentRepository.getTotalDueAmount(admission.getRegistrationNumber());

        dto.setTotalPaidAmount(totalPaid != null ? totalPaid : 0.0);
        dto.setTotalDueAmount(totalDue != null ? totalDue : 0.0);

        // Fetch fees status
        feesRepository.findByRegistrationNumberAndIsDeletedFalse(admission.getRegistrationNumber())
                .ifPresent(fees -> {
                    String feesStatus = fees.getStatus() != null ? fees.getStatus() : "Pending";
                    
                    if ("CANCELLED".equalsIgnoreCase(admission.getStudentCategory())) {
                        feesStatus = "Cancelled";
                    } else if (!"Clear".equalsIgnoreCase(feesStatus) && fees.getDueDate() != null && fees.getDueDate().isBefore(LocalDate.now())) {
                        feesStatus = "Overdue";
                    }
                    
                    dto.setFeesStatus(feesStatus);
                    
                    // Also sync total config if not already set
                    if (dto.getInstallmentStartDate() == null) dto.setInstallmentStartDate(fees.getInstallmentStartDate());
                    if (dto.getNumberOfInstallments() == null) dto.setNumberOfInstallments(fees.getNumberOfInstallments());
                });

        if (dto.getFeesStatus() == null) {
            dto.setFeesStatus("Pending");
        }
        
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
                        arr -> (Long) arr[1]));
        stats.put("byStatus", statusMap);

        List<Object[]> yearStats = admissionRepository.getAdmissionStatsByYear();
        Map<String, Long> yearMap = yearStats.stream()
                .collect(Collectors.toMap(
                        arr -> (String) arr[0],
                        arr -> (Long) arr[1]));
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
                // Validate and set defaults
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

                // Try to find enquiry
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

                // IMPORTANT: Determine if this is NEW or OLD format
                boolean isNewAdmission = false;
                importSource = "NEW_ENTRY";

                String regNumber = dto.getRegistrationNumber();
                if (regNumber != null && !regNumber.trim().isEmpty()) {
                    // Check if it's a NEW admission (starts with REG)
                    isNewAdmission = regNumber.startsWith("REG");
                    importSource = isNewAdmission ? "NEW_ENTRY" : "IMPORTED_OLD_DATA";

                    try {
                        Admission existing = admissionRepository
                                .findByRegistrationNumberAndIsDeletedFalse(regNumber);

                        if (existing != null) {
                            regNumber = regNumber + "_DUP" + rowNumber;
                            hasWarnings = true;
                            dto.setRegistrationNumber(regNumber);
                        }
                    } catch (Exception e) {
                        log.warn("Could not check existing admission: {}", e.getMessage());
                    }
                } else {
                    // Generate new REG number - This is definitely NEW
                    regNumber = generateRegistrationNumber();
                    isNewAdmission = true;
                    importSource = "NEW_ENTRY";
                    dto.setRegistrationNumber(regNumber);
                }

                Admission admission = admissionMapper.toEntity(dto);
                admission.setEnquiryId(enquiryId);
                admission.setCreatedBy("BULK_IMPORT");
                admission.setRegistrationNumber(regNumber);
                admission.setImportSource(importSource);

                LocalDate cutoffDate = systemConfigurationService.getCutoffDate();
                String category = studentCategoryService.determineCategory(admission, cutoffDate);
                admission.setStudentCategory(category);
                admission.setCategoryUpdatedAt(LocalDateTime.now());

                try {
                    // Pass isNewAdmission flag to save method
                    saveAdmissionInNewTransaction(admission, isNewAdmission);
                    successCount++;

                    if (hasWarnings) {
                        withWarnings++;
                    }

                    log.info(" Row {}: Saved (isNew={}, Mobile: {}, Reg: {})",
                            rowNumber, isNewAdmission, admission.getMobilePrimary(), admission.getRegistrationNumber());

                } catch (Exception saveEx) {
                    log.error(" Row {}: Save failed - {}", rowNumber, saveEx.getMessage());

                    errors.add(BulkImportResponseDTO.ImportError.builder()
                            .rowNumber(rowNumber)
                            .fieldName("database")
                            .errorMessage("Save failed: " + saveEx.getMessage())
                            .rejectedValue(dto.getMobilePrimary())
                            .build());
                }

            } catch (Exception e) {
                log.error(" Row {}: Processing error - {}", rowNumber, e.getMessage(), e);

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
                        successCount, dtos.size(), withWarnings, failedCount))
                .build();
    }

    // ==================== GENERATE INSTALLMENTS - STORE CONFIG
    // ====================

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
                    // Store config in each installment
                    .totalAmount(totalAmount)
                    .totalInstallmentAmount(totalAmount)
                    .installmentStartDate(config.getStartDate())
                    .numberOfInstallments(config.getNumberOfInstallments())
                    .daysBetweenInstallments(config.getDaysBetween())
                    .createdBy("SYSTEM")
                    .build();

            installments.add(installment);
            currentDate = currentDate.plusDays(config.getDaysBetween());
        }

        List<FeeInstallment> saved = feeInstallmentRepository.saveAll(installments);

        // Update Fees table with installment config
        feesRepository.findByRegistrationNumberAndIsDeletedFalse(registrationNumber)
                .ifPresent(fees -> {
                    fees.setInstallmentStartDate(config.getStartDate());
                    fees.setNumberOfInstallments(config.getNumberOfInstallments());
                    fees.setDaysBetweenInstallments(config.getDaysBetween());
                    fees.setTotalInstallmentAmount(totalAmount);
                    fees.setDueDate(config.getStartDate());
                    feesRepository.save(fees);
                });

        log.info(" Generated and saved {} installments with config", saved.size());

        return saved.stream()
                .map(admissionMapper::toInstallmentDTO)
                .collect(Collectors.toList());
    }

    // ==================== GET ADMISSION BY ID - HANDLE OLD DATA
    // ====================

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

        AdmissionResponseDTO dto = toResponseDTOWithInstallments(admission);

        // Load installment config from Fees table (FIRST RECORD ONLY)
        feesRepository.findByRegistrationNumberAndIsDeletedFalse(admission.getRegistrationNumber())
                .ifPresent(fees -> {
                    dto.setInstallmentStartDate(fees.getInstallmentStartDate());
                    dto.setNumberOfInstallments(fees.getNumberOfInstallments());
                    dto.setDaysBetweenInstallments(fees.getDaysBetweenInstallments());
                    dto.setTotalInstallmentAmount(fees.getTotalInstallmentAmount());
                });

        return dto;
    }

    // ==================== HANDLE OLD CSV IMPORTS (NO INSTALLMENT DATA)
    // ====================

    // private void createFeesRecord(Admission admission) {
    // try {
    // // Check if already exists
    // Optional<Fees> existingFees = feesRepository
    // .findByRegistrationNumberAndIsDeletedFalse(admission.getRegistrationNumber());
    //
    // if (existingFees.isPresent()) {
    // log.info(" Fees record already exists for regNo: {} - Skipping",
    // admission.getRegistrationNumber());
    // return;
    // }
    //
    // String courseName = (admission.getCourses() != null &&
    // !admission.getCourses().isEmpty())
    // ? String.join(", ", admission.getCourses())
    // : "N/A";
    //
    // Fees fees = Fees.builder()
    // .registrationNumber(admission.getRegistrationNumber())
    // .admissionId(admission.getId())
    // .studentName(admission.getFullName())
    // .mobile(admission.getMobilePrimary())
    // .totalFees(admission.getTotalReceivableFees() != null ?
    // admission.getTotalReceivableFees() : 0.0)
    // .feesDue(admission.getTotalReceivableFees() != null ?
    // admission.getTotalReceivableFees() : 0.0)
    // .totalPaid(0.0)
    // .dueDate(null)
    // .feesRefund(0.0)
    // .status("Pending")
    // .course(courseName)
    // .installmentStartDate(null)
    // .numberOfInstallments(null)
    // .daysBetweenInstallments(null)
    // .totalInstallmentAmount(null)
    // .createdBy("SYSTEM")
    // .build();
    //
    // // ADD: Flush immediately after save
    // Fees savedFees = feesRepository.save(fees);
    // feesRepository.flush();
    //
    // log.info(" Created and flushed fees record for regNo: {}",
    // admission.getRegistrationNumber());
    //
    // } catch (Exception e) {
    // log.error(" Failed to create fees record for regNo: {}",
    // admission.getRegistrationNumber(), e);
    // // ADD: Rethrow to prevent partial save
    // throw new RuntimeException("Failed to create fees record: " + e.getMessage(),
    // e);
    // }
    // }

    /**
     * Save admission in new transaction with proper error handling
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveAdmissionInNewTransaction(Admission admission, boolean isNewAdmission) {
        try {
            // VALIDATE BEFORE SAVE
            if (admission.getRegistrationNumber() == null || admission.getRegistrationNumber().trim().isEmpty()) {
                throw new IllegalArgumentException("Registration number cannot be null");
            }
            if (admission.getFirstName() == null || admission.getFirstName().trim().isEmpty()) {
                throw new IllegalArgumentException("First name cannot be null");
            }
            if (admission.getLastName() == null || admission.getLastName().trim().isEmpty()) {
                throw new IllegalArgumentException("Last name cannot be null");
            }
            if (admission.getMobilePrimary() == null || admission.getMobilePrimary().trim().isEmpty()) {
                throw new IllegalArgumentException("Mobile number cannot be null");
            }

            // ENSURE CATEGORY IS SET BEFORE SAVE
            if (admission.getStudentCategory() == null || admission.getStudentCategory().isEmpty()) {
                LocalDate cutoffDate = systemConfigurationService.getCutoffDate();
                String category = studentCategoryService.determineCategory(admission, cutoffDate);
                admission.setStudentCategory(category);
                admission.setCategoryUpdatedAt(LocalDateTime.now());
                log.debug(" Set category during save: {} -> {}", admission.getRegistrationNumber(), category);
            }

            // SAVE ADMISSION
            Admission saved = admissionRepository.save(admission);
            admissionRepository.flush();

            log.debug(" Saved admission for regNo: {} with category: {}",
                    saved.getRegistrationNumber(), saved.getStudentCategory());

            // ONLY create fees record for NEW admissions (REG* numbers)
            if (isNewAdmission && saved.getRegistrationNumber().startsWith("REG")) {
                try {
                    createFeesRecordInNewTransaction(saved);
                } catch (Exception feesEx) {
                    log.error(" Failed to create fees for regNo: {} - {}",
                            saved.getRegistrationNumber(), feesEx.getMessage());
                }
            } else {
                log.debug(" Skipping fees creation for regNo: {}", saved.getRegistrationNumber());
            }

        } catch (Exception e) {
            log.error(" Failed to save admission: {}", e.getMessage());
            throw new RuntimeException("Save failed: " + e.getMessage(), e);
        }
    }

    /**
     * Create fees record in NEW transaction (isolated from admission save)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createFeesRecordInNewTransaction(Admission admission) {
        try {
            // Double-check: Only proceed if registration number starts with REG
            if (!admission.getRegistrationNumber().startsWith("REG")) {
                log.warn(" Skipping fees record - Not a REG number: {}",
                        admission.getRegistrationNumber());
                return;
            }

            // Check if already exists
            Optional<Fees> existingFees = feesRepository
                    .findByRegistrationNumberAndIsDeletedFalse(admission.getRegistrationNumber());

            if (existingFees.isPresent()) {
                log.info("ℹ️ Fees record already exists for regNo: {} - Skipping",
                        admission.getRegistrationNumber());
                return;
            }

            String courseName = (admission.getCourses() != null && !admission.getCourses().isEmpty())
                    ? String.join(", ", admission.getCourses())
                    : "N/A";

            // CREATE FEES RECORD WITH DEFAULTS
            Fees fees = Fees.builder()
                    .registrationNumber(admission.getRegistrationNumber())
                    .admissionId(admission.getId())
                    .studentName(admission.getFullName())
                    .mobile(admission.getMobilePrimary())
                    .totalFees(admission.getTotalReceivableFees() != null ? admission.getTotalReceivableFees() : 0.0)
                    .feesDue(admission.getTotalReceivableFees() != null ? admission.getTotalReceivableFees() : 0.0)
                    .totalPaid(0.0)
                    .dueDate(null)
                    .feesRefund(0.0)
                    .status("Pending")
                    .course(courseName)
                    .installmentStartDate(null)
                    .numberOfInstallments(null)
                    .daysBetweenInstallments(null)
                    .totalInstallmentAmount(null)
                    .createdBy("SYSTEM")
                    .isDeleted(false)
                    .build();

            // SAVE AND FLUSH
            Fees saved = feesRepository.save(fees);
            feesRepository.flush();

            log.info(" Created fees record for regNo: {}", admission.getRegistrationNumber());

        } catch (Exception e) {
            log.error(" Failed to create fees record for regNo: {}",
                    admission.getRegistrationNumber(), e);
            throw new RuntimeException("Fees creation failed: " + e.getMessage(), e);
        }
    }
}