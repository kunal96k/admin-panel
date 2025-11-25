package com.tts.sms.service;

import com.tts.sms.dto.*;
import com.tts.sms.model.Admission;
import com.tts.sms.model.FeeInstallment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class AdmissionMapper {

    public Admission toEntity(AdmissionRequestDTO dto) {
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
                .courses(dto.getCourses() != null ? new ArrayList<>(dto.getCourses()) : new ArrayList<>())
                .totalPayableFees(dto.getTotalPayableFees())
                .totalReceivableFees(dto.getTotalReceivableFees())
                .discountPercent(dto.getDiscountPercent())
                .discountAmount(dto.getDiscountAmount())
                .batches(dto.getBatches() != null ? new ArrayList<>(dto.getBatches()) : new ArrayList<>())
                .subjects(dto.getSubjects() != null ? new ArrayList<>(dto.getSubjects()) : new ArrayList<>())
                .academicYear(dto.getAcademicYear())
                .documentType(dto.getDocumentType())
                .leadSource(dto.getLeadSource())
                .admissionDate(dto.getAdmissionDate())
                .rollNumber(dto.getRollNumber())
                .notes(dto.getNotes())
                .build();
    }

    public AdmissionResponseDTO toResponseDTO(Admission entity) {
        return AdmissionResponseDTO.builder()
                .id(entity.getId())
                .registrationNumber(entity.getRegistrationNumber())
                .rollNumber(entity.getRollNumber())
                .admissionDate(entity.getAdmissionDate())
                .enquiryId(entity.getEnquiryId())
                .studentName(entity.getFullName())
                .firstName(entity.getFirstName())
                .middleName(entity.getMiddleName())
                .lastName(entity.getLastName())
                .college(entity.getCollege())
                .qualification(entity.getQualification())
                .aadhaar(entity.getAadhaar())
                .birthDate(entity.getBirthDate())
                .gender(entity.getGender())
                .cast(entity.getCast())
                .category(entity.getCategory())
                .physicallyHandicapped(entity.getPhysicallyHandicapped())
                .bloodGroup(entity.getBloodGroup())
                .mobilePrimary(entity.getMobilePrimary())
                .mobileSecondary(entity.getMobileSecondary())
                .emailPrimary(entity.getEmailPrimary())
                .emailSecondary(entity.getEmailSecondary())
                .currentAddress(entity.getCurrentAddress())
                .permanentAddress(entity.getPermanentAddress())
                .pinCodeCurrent(entity.getPinCodeCurrent())
                .pinCodePermanent(entity.getPinCodePermanent())
                .packageName(entity.getPackageName())
                .coursesList(entity.getCourses() != null ? entity.getCourses() : new ArrayList<>())
                .courses(entity.getCourses() != null ? String.join(", ", entity.getCourses()) : "")
                .totalPayableFees(entity.getTotalPayableFees())
                .totalReceivableFees(entity.getTotalReceivableFees())
                .discountPercent(entity.getDiscountPercent())
                .discountAmount(entity.getDiscountAmount())
                .batchesList(entity.getBatches() != null ? entity.getBatches() : new ArrayList<>())
                .batches(entity.getBatches() != null ? String.join(", ", entity.getBatches()) : "")
                .subjectsList(entity.getSubjects() != null ? entity.getSubjects() : new ArrayList<>())
                .subjects(entity.getSubjects() != null ? String.join(", ", entity.getSubjects()) : "")
                .academicYear(entity.getAcademicYear())
                .documentType(entity.getDocumentType())
                .leadSource(entity.getLeadSource())
                .notes(entity.getNotes())
                .photoPath(entity.getPhotoPath())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public void updateEntityFromDTO(AdmissionRequestDTO dto, Admission entity) {
        entity.setFirstName(dto.getFirstName());
        entity.setMiddleName(dto.getMiddleName());
        entity.setLastName(dto.getLastName());
        entity.setCollege(dto.getCollege());
        entity.setQualification(dto.getQualification());
        entity.setAadhaar(dto.getAadhaar());
        entity.setBirthDate(dto.getBirthDate());
        entity.setGender(dto.getGender());
        entity.setCast(dto.getCast());
        entity.setCategory(dto.getCategory());
        entity.setPhysicallyHandicapped(dto.getPhysicallyHandicapped());
        entity.setBloodGroup(dto.getBloodGroup());
        entity.setMobilePrimary(dto.getMobilePrimary());
        entity.setMobileSecondary(dto.getMobileSecondary());
        entity.setEmailPrimary(dto.getEmailPrimary());
        entity.setEmailSecondary(dto.getEmailSecondary());
        entity.setCurrentAddress(dto.getCurrentAddress());
        entity.setPermanentAddress(dto.getPermanentAddress());
        entity.setPinCodeCurrent(dto.getPinCodeCurrent());
        entity.setPinCodePermanent(dto.getPinCodePermanent());
        entity.setPackageName(dto.getPackageName());

        if (dto.getCourses() != null) {
            entity.setCourses(new ArrayList<>(dto.getCourses()));
        }

        entity.setTotalPayableFees(dto.getTotalPayableFees());
        entity.setTotalReceivableFees(dto.getTotalReceivableFees());
        entity.setDiscountPercent(dto.getDiscountPercent());
        entity.setDiscountAmount(dto.getDiscountAmount());

        if (dto.getBatches() != null) {
            entity.setBatches(new ArrayList<>(dto.getBatches()));
        }

        if (dto.getSubjects() != null) {
            entity.setSubjects(new ArrayList<>(dto.getSubjects()));
        }

        entity.setAcademicYear(dto.getAcademicYear());
        entity.setDocumentType(dto.getDocumentType());
        entity.setLeadSource(dto.getLeadSource());
        entity.setAdmissionDate(dto.getAdmissionDate());
        entity.setRollNumber(dto.getRollNumber());
        entity.setNotes(dto.getNotes());
    }

    public FeeInstallmentDTO toInstallmentDTO(FeeInstallment entity) {
        return FeeInstallmentDTO.builder()
                .id(entity.getId())
                .admissionId(entity.getAdmissionId())
                .installmentNumber(entity.getInstallmentNumber())
                .dueDate(entity.getDueDate())
                .amount(entity.getAmount())
                .paidAmount(entity.getPaidAmount())
                .paidDate(entity.getPaidDate())
                .paymentMode(entity.getPaymentMode())
                .transactionId(entity.getTransactionId())
                .status(entity.getStatus())
                .notes(entity.getNotes())
                .build();
    }
}