package com.tts.sms.service;

import com.tts.sms.dto.*;
import com.tts.sms.model.Admission;
import com.tts.sms.model.FeeInstallment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AdmissionMapper {

    public AdmissionResponseDTO toResponseDTO(Admission admission) {
        if (admission == null) return null;

        // Get lists directly (no parsing needed)
        List<String> coursesList = admission.getCourses() != null
                ? new ArrayList<>(admission.getCourses())
                : new ArrayList<>();

        List<String> batchesList = admission.getBatches() != null
                ? new ArrayList<>(admission.getBatches())
                : new ArrayList<>();

        List<String> subjectsList = admission.getSubjects() != null
                ? new ArrayList<>(admission.getSubjects())
                : new ArrayList<>();

        // Convert to comma-separated for backward compatibility
        String coursesStr = coursesList.isEmpty() ? "" : String.join(", ", coursesList);
        String batchesStr = batchesList.isEmpty() ? "" : String.join(", ", batchesList);
        String subjectsStr = subjectsList.isEmpty() ? "" : String.join(", ", subjectsList);

        return AdmissionResponseDTO.builder()
                .id(admission.getId())
                .registrationNumber(admission.getRegistrationNumber())
                .rollNumber(admission.getRollNumber())
                .admissionDate(admission.getAdmissionDate())
                .enquiryId(admission.getEnquiryId())
                .studentName(admission.getFullName())
                .firstName(admission.getFirstName())
                .middleName(admission.getMiddleName())
                .lastName(admission.getLastName())
                .college(admission.getCollege())
                .qualification(admission.getQualification())
                .aadhaar(admission.getAadhaar())
                .birthDate(admission.getBirthDate())
                .gender(admission.getGender())
                .cast(admission.getCast())
                .category(admission.getCategory())
                .physicallyHandicapped(admission.getPhysicallyHandicapped())
                .bloodGroup(admission.getBloodGroup())
                .mobilePrimary(admission.getMobilePrimary())
                .mobileSecondary(admission.getMobileSecondary())
                .emailPrimary(admission.getEmailPrimary())
                .emailSecondary(admission.getEmailSecondary())
                .currentAddress(admission.getCurrentAddress())
                .permanentAddress(admission.getPermanentAddress())
                .pinCodeCurrent(admission.getPinCodeCurrent())
                .pinCodePermanent(admission.getPinCodePermanent())
                .packageName(admission.getPackageName())
                .courses(coursesStr)
                .coursesList(coursesList)
                .totalPayableFees(admission.getTotalPayableFees())
                .totalReceivableFees(admission.getTotalReceivableFees())
                .discountPercent(admission.getDiscountPercent())
                .discountAmount(admission.getDiscountAmount())
                .batches(batchesStr)
                .batchesList(batchesList)
                .subjects(subjectsStr)
                .subjectsList(subjectsList)
                .academicYear(admission.getAcademicYear())
                .documentType(admission.getDocumentType())
                .leadSource(admission.getLeadSource())
                .notes(admission.getNotes())
                .photoPath(admission.getPhotoPath())
                .status(admission.getStatus())
                .createdAt(admission.getCreatedAt())
                .updatedAt(admission.getUpdatedAt())
                .build();
    }

    public Admission toEntity(AdmissionRequestDTO dto) {
        if (dto == null) return null;

        // **CRITICAL FIX: Store as lists directly**
        List<String> coursesList = dto.getCourses() != null && !dto.getCourses().isEmpty()
                ? new ArrayList<>(dto.getCourses())
                : new ArrayList<>();

        List<String> batchesList = dto.getBatches() != null && !dto.getBatches().isEmpty()
                ? new ArrayList<>(dto.getBatches())
                : new ArrayList<>();

        List<String> subjectsList = dto.getSubjects() != null && !dto.getSubjects().isEmpty()
                ? new ArrayList<>(dto.getSubjects())
                : new ArrayList<>();

        return Admission.builder()
                .enquiryId(dto.getEnquiryId())
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
                .courses(coursesList) // Store as list
                .totalPayableFees(dto.getTotalPayableFees())
                .totalReceivableFees(dto.getTotalReceivableFees())
                .discountPercent(dto.getDiscountPercent())
                .discountAmount(dto.getDiscountAmount())
                .batches(batchesList) // Store as list
                .subjects(subjectsList) // Store as list
                .academicYear(dto.getAcademicYear())
                .documentType(dto.getDocumentType())
                .leadSource(dto.getLeadSource())
                .admissionDate(dto.getAdmissionDate())
                .rollNumber(dto.getRollNumber())
                .notes(dto.getNotes())
                .status("Active")
                .build();
    }
        /**
         * Update entity from DTO
         */
        public void updateEntityFromDTO(AdmissionRequestDTO dto, Admission admission) {
            if (dto == null || admission == null) return;

            admission.setFirstName(dto.getFirstName());
            admission.setMiddleName(dto.getMiddleName());
            admission.setLastName(dto.getLastName());
            admission.setCollege(dto.getCollege());
            admission.setQualification(dto.getQualification());
            admission.setAadhaar(dto.getAadhaar());
            admission.setBirthDate(dto.getBirthDate());
            admission.setGender(dto.getGender());
            admission.setCast(dto.getCast());
            admission.setCategory(dto.getCategory());
            admission.setPhysicallyHandicapped(dto.getPhysicallyHandicapped());
            admission.setBloodGroup(dto.getBloodGroup());
            admission.setMobilePrimary(dto.getMobilePrimary());
            admission.setMobileSecondary(dto.getMobileSecondary());
            admission.setEmailPrimary(dto.getEmailPrimary());
            admission.setEmailSecondary(dto.getEmailSecondary());
            admission.setCurrentAddress(dto.getCurrentAddress());
            admission.setPermanentAddress(dto.getPermanentAddress());
            admission.setPinCodeCurrent(dto.getPinCodeCurrent());
            admission.setPinCodePermanent(dto.getPinCodePermanent());

            if (dto.getCourses() != null) {
                admission.setCourses(new ArrayList<>(dto.getCourses()));
            }

            admission.setPackageName(dto.getPackageName());
            admission.setTotalPayableFees(dto.getTotalPayableFees());
            admission.setTotalReceivableFees(dto.getTotalReceivableFees());
            admission.setDiscountPercent(dto.getDiscountPercent());
            admission.setDiscountAmount(dto.getDiscountAmount());

            if (dto.getBatches() != null) {
                admission.setBatches(new ArrayList<>(dto.getBatches()));
            }

            if (dto.getSubjects() != null) {
                admission.setSubjects(new ArrayList<>(dto.getSubjects()));
            }

            admission.setAcademicYear(dto.getAcademicYear());
            admission.setDocumentType(dto.getDocumentType());
            admission.setLeadSource(dto.getLeadSource());
            admission.setRollNumber(dto.getRollNumber());
            admission.setNotes(dto.getNotes());
        }

    public FeeInstallmentDTO toInstallmentDTO(FeeInstallment installment) {
        if (installment == null) return null;

        return FeeInstallmentDTO.builder()
                .id(installment.getId())
                .admissionId(installment.getAdmissionId())
                .installmentNumber(installment.getInstallmentNumber())
                .dueDate(installment.getDueDate())
                .amount(installment.getAmount())
                .status(installment.getStatus())
                .paidAmount(installment.getPaidAmount())
                .paidDate(installment.getPaidDate())
                .paymentMode(installment.getPaymentMode())
                .transactionId(installment.getTransactionId())
                .notes(installment.getNotes())
                .build();
    }

    public FeeInstallment toInstallmentEntity(FeeInstallmentDTO dto) {
        if (dto == null) return null;

        return FeeInstallment.builder()
                .admissionId(dto.getAdmissionId())
                .installmentNumber(dto.getInstallmentNumber())
                .dueDate(dto.getDueDate())
                .amount(dto.getAmount())
                .status(dto.getStatus() != null ? dto.getStatus() : "Pending")
                .paidAmount(dto.getPaidAmount())
                .paidDate(dto.getPaidDate())
                .paymentMode(dto.getPaymentMode())
                .transactionId(dto.getTransactionId())
                .notes(dto.getNotes())
                .build();
    }
}