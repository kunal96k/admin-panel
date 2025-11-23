package com.tts.sms.service;

import com.tts.sms.dto.EnquiryRequestDTO;
import com.tts.sms.dto.EnquiryResponseDTO;
import com.tts.sms.model.Enquiry;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class EnquiryMapper {

    /**
     * Convert Entity to Response DTO
     */
    public EnquiryResponseDTO toResponseDTO(Enquiry enquiry) {
        if (enquiry == null) return null;

        String coursesStr = enquiry.getCourse() != null ? enquiry.getCourse() : "";
        List<String> coursesList = Arrays.stream(coursesStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        return EnquiryResponseDTO.builder()
                .id(enquiry.getId())
                .name(enquiry.getDisplayName())
                .firstName(enquiry.getFirstName())
                .middleName(enquiry.getMiddleName())
                .lastName(enquiry.getLastName())
                .mobile(enquiry.getMobile())
                .secondaryMobile(enquiry.getSecondaryMobile())
                .email(enquiry.getEmail())
                .secondaryEmail(enquiry.getSecondaryEmail())
                .currentAddress(enquiry.getCurrentAddress())
                .permanentAddress(enquiry.getPermanentAddress())
                .pinCurrent(enquiry.getPinCurrent())
                .pinPermanent(enquiry.getPinPermanent())
                .college(enquiry.getCollege())
                .qualification(enquiry.getQualification())
                .aadhaar(enquiry.getAadhaar())
                .birthDate(enquiry.getBirthDate())
                .gender(enquiry.getGender())
                .courses(coursesStr)
                .coursesList(coursesList)
                .packageName(enquiry.getPackageName())
                .demoLectureRequired(enquiry.getDemoLectureRequired())
                .interestLevel(enquiry.getInterestLevel())
                .source(enquiry.getSource())
                .referenceName(enquiry.getReferenceName())
                .date(enquiry.getEnquiryDate())
                .followupDate(enquiry.getFollowupDate())
                .assign(enquiry.getAssignTo())
                .status(enquiry.getStatus())
                .note(enquiry.getNote())
                .importSource(enquiry.getImportSource())
                .createdAt(enquiry.getCreatedAt())
                .updatedAt(enquiry.getUpdatedAt())
                .build();
    }

    /**
     * Convert Request DTO to Entity
     */
    public Enquiry toEntity(EnquiryRequestDTO dto) {
        if (dto == null) return null;

        String coursesStr = dto.getCourses() != null && !dto.getCourses().isEmpty()
                ? String.join(", ", dto.getCourses())
                : "";

        Enquiry.EnquiryBuilder builder = Enquiry.builder()
                .mobile(dto.getMobile())
                .secondaryMobile(dto.getSecondaryMobile())
                .email(dto.getEmail())
                .secondaryEmail(dto.getSecondaryEmail())
                .currentAddress(dto.getCurrentAddress())
                .permanentAddress(dto.getPermanentAddress())
                .pinCurrent(dto.getPinCurrent())
                .pinPermanent(dto.getPinPermanent())
                .college(dto.getCollege())
                .qualification(dto.getQualification())
                .aadhaar(dto.getAadhaar())
                .birthDate(dto.getBirthDate())
                .gender(dto.getGender())
                .course(coursesStr)
                .packageName(dto.getPackageName())
                .demoLectureRequired(dto.getDemoLectureRequired())
                .interestLevel(dto.getInterestLevel())
                .source(dto.getSource() != null ? dto.getSource() : "Unknown")
                .referenceName(dto.getReferenceName())
                .enquiryDate(dto.getEnquiryDate())
                .followupDate(dto.getFollowupDate())
                .assignTo(dto.getAssignTo())
                .status(dto.getStatus() != null ? dto.getStatus() : "New")
                .note(dto.getNote());

        // Handle name fields - support both old and new format
        if (dto.getName() != null && !dto.getName().isBlank()) {
            // Old format - single name field
            builder.fullName(dto.getName());
            // Try to split into components
            String[] parts = dto.getName().trim().split("\\s+");
            if (parts.length > 0) builder.firstName(parts[0]);
            if (parts.length > 2) {
                builder.middleName(String.join(" ", Arrays.copyOfRange(parts, 1, parts.length - 1)));
                builder.lastName(parts[parts.length - 1]);
            } else if (parts.length == 2) {
                builder.lastName(parts[1]);
            }
        } else if (dto.getFirstName() != null || dto.getLastName() != null) {
            // New format - separate name fields
            builder.firstName(dto.getFirstName())
                    .middleName(dto.getMiddleName())
                    .lastName(dto.getLastName());

            // Build full name from components
            StringBuilder fullName = new StringBuilder();
            if (dto.getFirstName() != null) fullName.append(dto.getFirstName());
            if (dto.getMiddleName() != null && !dto.getMiddleName().isEmpty()) {
                if (fullName.length() > 0) fullName.append(" ");
                fullName.append(dto.getMiddleName());
            }
            if (dto.getLastName() != null) {
                if (fullName.length() > 0) fullName.append(" ");
                fullName.append(dto.getLastName());
            }
            if (fullName.length() > 0) {
                builder.fullName(fullName.toString());
            }
        }

        return builder.build();
    }

    /**
     * Update existing entity from DTO
     */
    public void updateEntityFromDTO(EnquiryRequestDTO dto, Enquiry enquiry) {
        if (dto == null || enquiry == null) return;

        // Update name fields
        if (dto.getName() != null && !dto.getName().isBlank()) {
            enquiry.setFullName(dto.getName());
            String[] parts = dto.getName().trim().split("\\s+");
            if (parts.length > 0) enquiry.setFirstName(parts[0]);
            if (parts.length > 2) {
                enquiry.setMiddleName(String.join(" ", Arrays.copyOfRange(parts, 1, parts.length - 1)));
                enquiry.setLastName(parts[parts.length - 1]);
            } else if (parts.length == 2) {
                enquiry.setLastName(parts[1]);
            }
        } else {
            enquiry.setFirstName(dto.getFirstName());
            enquiry.setMiddleName(dto.getMiddleName());
            enquiry.setLastName(dto.getLastName());

            // Build full name
            StringBuilder fullName = new StringBuilder();
            if (dto.getFirstName() != null) fullName.append(dto.getFirstName());
            if (dto.getMiddleName() != null && !dto.getMiddleName().isEmpty()) {
                if (fullName.length() > 0) fullName.append(" ");
                fullName.append(dto.getMiddleName());
            }
            if (dto.getLastName() != null) {
                if (fullName.length() > 0) fullName.append(" ");
                fullName.append(dto.getLastName());
            }
            enquiry.setFullName(fullName.length() > 0 ? fullName.toString() : null);
        }

        // Update other fields
        enquiry.setMobile(dto.getMobile());
        enquiry.setSecondaryMobile(dto.getSecondaryMobile());
        enquiry.setEmail(dto.getEmail());
        enquiry.setSecondaryEmail(dto.getSecondaryEmail());
        enquiry.setCurrentAddress(dto.getCurrentAddress());
        enquiry.setPermanentAddress(dto.getPermanentAddress());
        enquiry.setPinCurrent(dto.getPinCurrent());
        enquiry.setPinPermanent(dto.getPinPermanent());
        enquiry.setCollege(dto.getCollege());
        enquiry.setQualification(dto.getQualification());
        enquiry.setAadhaar(dto.getAadhaar());
        enquiry.setBirthDate(dto.getBirthDate());
        enquiry.setGender(dto.getGender());

        if (dto.getCourses() != null && !dto.getCourses().isEmpty()) {
            enquiry.setCourse(String.join(", ", dto.getCourses()));
        }

        enquiry.setPackageName(dto.getPackageName());
        enquiry.setDemoLectureRequired(dto.getDemoLectureRequired());
        enquiry.setInterestLevel(dto.getInterestLevel());
        enquiry.setSource(dto.getSource());
        enquiry.setReferenceName(dto.getReferenceName());
        enquiry.setEnquiryDate(dto.getEnquiryDate());
        enquiry.setFollowupDate(dto.getFollowupDate());
        enquiry.setAssignTo(dto.getAssignTo());

        if (dto.getStatus() != null) {
            enquiry.setStatus(dto.getStatus());
        }

        enquiry.setNote(dto.getNote());
    }
}