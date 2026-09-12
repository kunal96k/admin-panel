package com.tts.sms.service;

import com.tts.sms.dto.CourseDropdownDTO;
import com.tts.sms.dto.*;
import com.tts.sms.model.*;
import com.tts.sms.model.Package;
import com.tts.sms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdmissionEnhancedService {

    private final PackageRepository packageRepository;
    private final CourseRepository courseRepository;
    private final SubjectRepository subjectRepository;
    private final BatchRepository batchRepository;
    private final LeadSourceRepository leadSourceRepository;
    private final AdmissionRepository admissionRepository;
    private final FeesRepository feesRepository;
    private final FeeInstallmentRepository feeInstallmentRepository;

    // ==================== DROPDOWN DATA ====================

    @Transactional(readOnly = true)
    public List<PackageWithCoursesDTO> getActivePackagesWithCourses() {
        log.info("Fetching active packages with courses");

        List<Package> packages = packageRepository.findByIsActiveTrueOrderByPackageNameAsc();

        return packages.stream()
                .map(pkg -> PackageWithCoursesDTO.builder()
                        .id(pkg.getId())
                        .packageName(pkg.getPackageName())
                        .totalAmount(pkg.getTotalAmount())
                        .courses(pkg.getCourses().stream()
                                .filter(Course::getIsActive)
                                .map(course -> CourseDropdownDTO.builder()
                                        .id(course.getId())
                                        .courseName(course.getCourseName())
                                        .courseFees(course.getCourseFees())
                                        .build())
                                .collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CourseDropdownDTO> getActiveCoursesForDropdown() {
        log.info("Fetching active courses");

        List<Course> courses = courseRepository.findByIsActiveTrueOrderByCourseNameAsc();

        return courses.stream()
                .map(course -> CourseDropdownDTO.builder()
                        .id(course.getId())
                        .courseName(course.getCourseName())
                        .courseFees(course.getCourseFees())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SubjectDropdownDTO> getSubjectsByCourseId(Long courseId) {
        log.info("Fetching subjects for course: {}", courseId);

        List<Subject> subjects = subjectRepository.findByCourseIdAndIsActiveTrue(courseId);

        return subjects.stream()
                .map(subject -> SubjectDropdownDTO.builder()
                        .id(subject.getId())
                        .subjectName(subject.getSubjectName())
                        .courseId(subject.getCourse().getId())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<BatchDropdownDTO> getActiveBatches() {
        log.info("Fetching active batches");

        List<Batch> batches = batchRepository.findByIsActiveTrueOrderByBatchNameAsc();

        return batches.stream()
                .map(batch -> BatchDropdownDTO.builder()
                        .id(batch.getId())
                        .batchNo(batch.getBatchNo())
                        .batchName(batch.getBatchName())
                        .startTime(batch.getStartTime())
                        .endTime(batch.getEndTime())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<LeadSourceDropdownDTO> getActiveLeadSources() {
        log.info("Fetching active lead sources");

        List<LeadSource> sources = leadSourceRepository.findByIsActiveTrueOrderBySourceTitleAsc();

        return sources.stream()
                .map(source -> LeadSourceDropdownDTO.builder()
                        .id(source.getId())
                        .sourceTitle(source.getSourceTitle())
                        .build())
                .collect(Collectors.toList());
    }

    // ==================== ADMISSION WITH FEES & INSTALLMENTS ====================

    @Transactional
    public AdmissionResponseDTO createAdmissionWithFeesAndInstallments(AdmissionRequestDTO requestDTO) {
        log.info("Creating admission with fees and installments for mobile: {}",
                requestDTO.getMobilePrimary());

        // 1. Create Admission
        Admission admission = buildAdmissionEntity(requestDTO);
        admission.setRegistrationNumber(generateRegistrationNumber());
        admission.setCreatedBy("SYSTEM");

        Admission savedAdmission = admissionRepository.save(admission);
        log.info("[OK] Admission created: {}", savedAdmission.getRegistrationNumber());

        // 2. Create Fees Record
        Fees fees = buildFeesEntity(savedAdmission);
        Fees savedFees = feesRepository.save(fees);
        log.info("[OK] Fees record created for: {}", savedAdmission.getRegistrationNumber());

        // 3. Generate Installments
        if (requestDTO.getInstallmentConfig() != null) {
            generateAndSaveInstallments(
                    savedAdmission.getRegistrationNumber(),
                    requestDTO.getInstallmentConfig(),
                    savedAdmission.getTotalReceivableFees()
            );
            log.info("[OK] Installments generated for: {}", savedAdmission.getRegistrationNumber());
        }

        return buildResponseDTO(savedAdmission);
    }

    private Admission buildAdmissionEntity(AdmissionRequestDTO dto) {
        return Admission.builder()
                .firstName(dto.getFirstName())
                .middleName(dto.getMiddleName())
                .lastName(dto.getLastName())
                .college(dto.getCollege())
                .qualification(dto.getQualification())
                .aadhaar(dto.getAadhaar())
                .birthDate(dto.getBirthDate())
                .gender(dto.getGender())
                .cast(dto.getCast())
                .category(dto.getCategory())
                .physicallyHandicapped(dto.getPhysicallyHandicapped())
                .bloodGroup(dto.getBloodGroup())
                .mobilePrimary(dto.getMobilePrimary())
                .mobileSecondary(dto.getMobileSecondary())
                .emailPrimary(dto.getEmailPrimary())
                .emailSecondary(dto.getEmailSecondary())
                .currentAddress(dto.getCurrentAddress())
                .permanentAddress(dto.getPermanentAddress())
                .pinCodeCurrent(dto.getPinCodeCurrent())
                .pinCodePermanent(dto.getPinCodePermanent())
                .packageName(dto.getPackageName())
                .courses(dto.getCourses())
                .totalPayableFees(dto.getTotalPayableFees())
                .totalReceivableFees(dto.getTotalReceivableFees())
                .discountPercent(dto.getDiscountPercent())
                .discountAmount(dto.getDiscountAmount())
                .batches(dto.getBatches())
                .subjects(dto.getSubjects())
                .academicYear(dto.getAcademicYear())
                .documentType(dto.getDocumentType())
                .leadSource(dto.getLeadSource())
                .admissionDate(dto.getAdmissionDate() != null ? dto.getAdmissionDate() : LocalDate.now())
                .rollNumber(dto.getRollNumber())
                .notes(dto.getNotes())
                .status("Active")
                .isDeleted(false)
                .build();
    }

    private Fees buildFeesEntity(Admission admission) {
        String courseName = (admission.getCourses() != null && !admission.getCourses().isEmpty())
                ? String.join(", ", admission.getCourses())
                : "N/A";

        return Fees.builder()
                .registrationNumber(admission.getRegistrationNumber())
                .admissionId(admission.getId())
                .studentName(admission.getFullName())
                .mobile(admission.getMobilePrimary())
                .totalFees(admission.getTotalReceivableFees())
                .feesDue(admission.getTotalReceivableFees())
                .totalPaid(0.0)
                .dueDate(null) // Will be set from first installment
                .feesRefund(0.0)
                .status("Pending")
                .course(courseName)
                .createdBy("SYSTEM")
                .build();
    }

    private void generateAndSaveInstallments(String registrationNumber,
                                             InstallmentConfigDTO config,
                                             Double totalAmount) {
        List<FeeInstallment> installments = new java.util.ArrayList<>();
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

        feeInstallmentRepository.saveAll(installments);

        // Update first installment due date in fees
        if (!installments.isEmpty()) {
            feesRepository.findByRegistrationNumberAndIsDeletedFalse(registrationNumber)
                    .ifPresent(fees -> {
                        fees.setDueDate(installments.get(0).getDueDate());
                        feesRepository.save(fees);
                    });
        }
    }

    private AdmissionResponseDTO buildResponseDTO(Admission admission) {
        return AdmissionResponseDTO.builder()
                .id(admission.getId())
                .registrationNumber(admission.getRegistrationNumber())
                .studentName(admission.getFullName())
                .firstName(admission.getFirstName())
                .middleName(admission.getMiddleName())
                .lastName(admission.getLastName())
                .mobilePrimary(admission.getMobilePrimary())
                .courses(String.join(", ", admission.getCourses()))
                .coursesList(admission.getCourses())
                .totalPayableFees(admission.getTotalPayableFees())
                .totalReceivableFees(admission.getTotalReceivableFees())
                .discountPercent(admission.getDiscountPercent())
                .discountAmount(admission.getDiscountAmount())
                .admissionDate(admission.getAdmissionDate())
                .status(admission.getStatus())
                .build();
    }

    private String generateRegistrationNumber() {
        // Simple counter-based generation
        long count = admissionRepository.count();
        return String.format("REG%04d", count + 8001);
    }
}