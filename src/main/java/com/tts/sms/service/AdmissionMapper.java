package com.tts.sms.service;

import com.tts.sms.dto.AdmissionRequestDTO;
import com.tts.sms.dto.AdmissionResponseDTO;
import com.tts.sms.dto.FeeInstallmentDTO;
import com.tts.sms.model.Admission;
import com.tts.sms.model.FeeInstallment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class AdmissionMapper {

    public Admission toEntity(AdmissionRequestDTO dto) {
        if (dto == null) return null;

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
                .courses(dto.getCourses() != null ? dto.getCourses() : new ArrayList<>())
                .totalPayableFees(dto.getTotalPayableFees())
                .totalReceivableFees(dto.getTotalReceivableFees())
                .discountPercent(dto.getDiscountPercent())
                .discountAmount(dto.getDiscountAmount())
                .batches(dto.getBatches() != null ? dto.getBatches() : new ArrayList<>())
                .subjects(dto.getSubjects() != null ? dto.getSubjects() : new ArrayList<>())
                .academicYear(dto.getAcademicYear())
                .documentType(dto.getDocumentType())
                .leadSource(dto.getLeadSource())
                .admissionDate(dto.getAdmissionDate())
                .rollNumber(dto.getRollNumber())
                .notes(dto.getNotes())
                .status("Active")
                .isDeleted(false)
                .build();
    }

    public AdmissionResponseDTO toResponseDTO(Admission admission) {
        if (admission == null) return null;

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
                .courses(listToString(admission.getCourses()))
                .coursesList(admission.getCourses())
                .totalPayableFees(admission.getTotalPayableFees())
                .totalReceivableFees(admission.getTotalReceivableFees())
                .discountPercent(admission.getDiscountPercent())
                .discountAmount(admission.getDiscountAmount())
                .batches(listToString(admission.getBatches()))
                .batchesList(admission.getBatches())
                .subjects(listToString(admission.getSubjects()))
                .subjectsList(admission.getSubjects())
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

    public void updateEntityFromDTO(AdmissionRequestDTO dto, Admission admission) {
        if (dto == null || admission == null) return;

        if (dto.getFirstName() != null) admission.setFirstName(dto.getFirstName());
        if (dto.getMiddleName() != null) admission.setMiddleName(dto.getMiddleName());
        if (dto.getLastName() != null) admission.setLastName(dto.getLastName());
        if (dto.getCollege() != null) admission.setCollege(dto.getCollege());
        if (dto.getQualification() != null) admission.setQualification(dto.getQualification());
        if (dto.getAadhaar() != null) admission.setAadhaar(dto.getAadhaar());
        if (dto.getBirthDate() != null) admission.setBirthDate(dto.getBirthDate());
        if (dto.getGender() != null) admission.setGender(dto.getGender());
        if (dto.getCast() != null) admission.setCast(dto.getCast());
        if (dto.getCategory() != null) admission.setCategory(dto.getCategory());
        if (dto.getPhysicallyHandicapped() != null) admission.setPhysicallyHandicapped(dto.getPhysicallyHandicapped());
        if (dto.getBloodGroup() != null) admission.setBloodGroup(dto.getBloodGroup());
        if (dto.getMobilePrimary() != null) admission.setMobilePrimary(dto.getMobilePrimary());
        if (dto.getMobileSecondary() != null) admission.setMobileSecondary(dto.getMobileSecondary());
        if (dto.getEmailPrimary() != null) admission.setEmailPrimary(dto.getEmailPrimary());
        if (dto.getEmailSecondary() != null) admission.setEmailSecondary(dto.getEmailSecondary());
        if (dto.getCurrentAddress() != null) admission.setCurrentAddress(dto.getCurrentAddress());
        if (dto.getPermanentAddress() != null) admission.setPermanentAddress(dto.getPermanentAddress());
        if (dto.getPinCodeCurrent() != null) admission.setPinCodeCurrent(dto.getPinCodeCurrent());
        if (dto.getPinCodePermanent() != null) admission.setPinCodePermanent(dto.getPinCodePermanent());
        if (dto.getPackageName() != null) admission.setPackageName(dto.getPackageName());
        if (dto.getCourses() != null) admission.setCourses(dto.getCourses());
        if (dto.getTotalPayableFees() != null) admission.setTotalPayableFees(dto.getTotalPayableFees());
        if (dto.getTotalReceivableFees() != null) admission.setTotalReceivableFees(dto.getTotalReceivableFees());
        if (dto.getDiscountPercent() != null) admission.setDiscountPercent(dto.getDiscountPercent());
        if (dto.getDiscountAmount() != null) admission.setDiscountAmount(dto.getDiscountAmount());
        if (dto.getBatches() != null) admission.setBatches(dto.getBatches());
        if (dto.getSubjects() != null) admission.setSubjects(dto.getSubjects());
        if (dto.getAcademicYear() != null) admission.setAcademicYear(dto.getAcademicYear());
        if (dto.getDocumentType() != null) admission.setDocumentType(dto.getDocumentType());
        if (dto.getLeadSource() != null) admission.setLeadSource(dto.getLeadSource());
        if (dto.getAdmissionDate() != null) admission.setAdmissionDate(dto.getAdmissionDate());
        if (dto.getRollNumber() != null) admission.setRollNumber(dto.getRollNumber());
        if (dto.getNotes() != null) admission.setNotes(dto.getNotes());
    }

    public FeeInstallmentDTO toInstallmentDTO(FeeInstallment installment) {
        if (installment == null) return null;

        return FeeInstallmentDTO.builder()
                .id(installment.getId())
                .admissionId(installment.getAdmissionId())
                .installmentNumber(installment.getInstallmentNumber())
                .dueDate(installment.getDueDate())
                .amount(installment.getAmount())
                .paidAmount(installment.getPaidAmount())
                .paidDate(installment.getPaidDate())
                .paymentMode(installment.getPaymentMode())
                .transactionId(installment.getTransactionId())
                .status(installment.getStatus())
                .notes(installment.getNotes())
                .isOverdue(installment.isOverdue())
                .build();
    }

    private String listToString(List<String> list) {
        if (list == null || list.isEmpty()) return null;
        return String.join(", ", list);
    }
}