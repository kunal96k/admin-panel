package com.tts.sms.service;

import com.tts.sms.dto.EnquiryRequestDTO;
import com.tts.sms.dto.EnquiryResponseDTO;
import com.tts.sms.model.Enquiry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class EnquiryMapper {

    public EnquiryResponseDTO toResponseDTO(Enquiry enquiry) {
        if (enquiry == null) return null;

        List<String> coursesList = enquiry.getCourses() != null
                ? new ArrayList<>(enquiry.getCourses())
                : new ArrayList<>();

        String coursesStr = coursesList.isEmpty() ? "" : String.join(", ", coursesList);

        return EnquiryResponseDTO.builder()
                .id(enquiry.getId())
                .enquiryNo(enquiry.getEnquiryNo())
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
     * STRICT CONVERSION: DTO → Entity
     * - No validation
     * - Keep all values as-is
     * - Only set defaults for truly null fields
     */
    public Enquiry toEntity(EnquiryRequestDTO dto) {
        if (dto == null) return null;

        // Handle courses - keep as-is or use "N/A"
        List<String> coursesList = dto.getCourses() != null && !dto.getCourses().isEmpty()
                ? new ArrayList<>(dto.getCourses())
                : List.of("N/A");

        Enquiry.EnquiryBuilder builder = Enquiry.builder()
                .enquiryNo(dto.getEnquiryNo())
                .mobile(dto.getMobile())
                .courses(coursesList)
                .source(dto.getSource() != null ? dto.getSource() : "N/A")
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
                .packageName(dto.getPackageName())
                .demoLectureRequired(dto.getDemoLectureRequired())
                .interestLevel(dto.getInterestLevel())
                .referenceName(dto.getReferenceName())
                .enquiryDate(dto.getEnquiryDate() != null ? dto.getEnquiryDate() : LocalDate.now())
                .followupDate(dto.getFollowupDate())
                .assignTo(dto.getAssignTo())
                .status(dto.getStatus() != null ? dto.getStatus() : "New")
                .note(dto.getNote());

        // Handle name fields
        if (dto.getName() != null && !dto.getName().equals("N/A")) {
            builder.fullName(dto.getName());
            String[] parts = dto.getName().trim().split("\\s+");
            if (parts.length > 0) builder.firstName(parts[0]);
            if (parts.length > 2) {
                builder.middleName(String.join(" ", java.util.Arrays.copyOfRange(parts, 1, parts.length - 1)));
                builder.lastName(parts[parts.length - 1]);
            } else if (parts.length == 2) {
                builder.lastName(parts[1]);
            }
        } else {
            builder.firstName(dto.getFirstName())
                    .middleName(dto.getMiddleName())
                    .lastName(dto.getLastName());

            StringBuilder fullName = new StringBuilder();
            if (dto.getFirstName() != null) fullName.append(dto.getFirstName());
            if (dto.getMiddleName() != null && !dto.getMiddleName().equals("N/A")) {
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

    public void updateEntityFromDTO(EnquiryRequestDTO dto, Enquiry enquiry) {
        if (dto == null || enquiry == null) return;

        // **FIXED: Update enquiryNo if provided**
        if (dto.getEnquiryNo() != null) {
            enquiry.setEnquiryNo(dto.getEnquiryNo());
        }

        // Update name fields
        if (dto.getName() != null && !dto.getName().isBlank()) {
            enquiry.setFullName(dto.getName());
            String[] parts = dto.getName().trim().split("\\s+");
            if (parts.length > 0) enquiry.setFirstName(parts[0]);
            if (parts.length > 2) {
                enquiry.setMiddleName(String.join(" ", java.util.Arrays.copyOfRange(parts, 1, parts.length - 1)));
                enquiry.setLastName(parts[parts.length - 1]);
            } else if (parts.length == 2) {
                enquiry.setLastName(parts[1]);
            }
        } else {
            enquiry.setFirstName(dto.getFirstName());
            enquiry.setMiddleName(dto.getMiddleName());
            enquiry.setLastName(dto.getLastName());

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

        // **FIXED: Update all courses**
        if (dto.getCourses() != null && !dto.getCourses().isEmpty()) {
            List<String> cleanedCourses = dto.getCourses().stream()
                    .filter(c -> c != null && !c.trim().isEmpty())
                    .map(String::trim)
                    .distinct()
                    .collect(Collectors.toList());
            enquiry.setCourses(cleanedCourses);
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