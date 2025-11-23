package com.tts.sms.service;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvException;
import com.tts.sms.dto.EnquiryRequestDTO;
import com.tts.sms.dto.EnquiryResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
public class CSVService {

    private static final DateTimeFormatter[] DATE_FORMATTERS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy")
    };

    /**
     * Parse OLD FORMAT CSV
     * Format: Enquiry No., Student Name, Mobile No., Course, Enquiry Source,
     *         Enquiry Date, Assign To, Enquiry Status
     */
    public List<EnquiryRequestDTO> parseOldFormatCSV(MultipartFile file) throws IOException {
        log.info("Parsing OLD format CSV: {}", file.getOriginalFilename());

        List<EnquiryRequestDTO> dtos = new ArrayList<>();

        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVReader csvReader = new CSVReader(reader)) {

            List<String[]> records = csvReader.readAll();

            if (records.isEmpty()) {
                throw new IllegalArgumentException("CSV file is empty");
            }

            // Skip header row
            for (int i = 1; i < records.size(); i++) {
                String[] row = records.get(i);

                if (row.length < 4) {
                    log.warn("Skipping incomplete row {}: {}", i + 1, Arrays.toString(row));
                    continue;
                }

                try {
                    EnquiryRequestDTO dto = EnquiryRequestDTO.builder()
                            .name(getValueOrNull(row, 1))  // Student Name
                            .mobile(cleanMobileNumber(getValueOrNull(row, 2)))  // Mobile No.
                            .courses(Arrays.asList(getValueOrNull(row, 3)))  // Course
                            .source(getValueOrNull(row, 4))  // Enquiry Source
                            .enquiryDate(parseDate(getValueOrNull(row, 5)))  // Enquiry Date
                            .assignTo(getValueOrNull(row, 6))  // Assign To
                            .status(getValueOrDefault(row, 7, "New"))  // Enquiry Status
                            .build();

                    // Validate required fields
                    if (dto.getName() == null || dto.getMobile() == null ||
                            dto.getCourses() == null || dto.getSource() == null) {
                        log.warn("Skipping row {} due to missing required fields", i + 1);
                        continue;
                    }

                    dtos.add(dto);

                } catch (Exception e) {
                    log.error("Error parsing row {}: {}", i + 1, e.getMessage());
                }
            }

            log.info("Successfully parsed {} records from OLD format CSV", dtos.size());
            return dtos;

        } catch (CsvException e) {
            log.error("CSV parsing error", e);
            throw new IOException("Failed to parse CSV file: " + e.getMessage(), e);
        }
    }

    /**
     * Parse NEW FORMAT CSV
     * Format: Enquiry No., First Name, Middle Name, Last Name, Mobile Primary,
     *         Mobile Secondary, Email Primary, Current Address, Permanent Address,
     *         College, Enquiry Date, Followup Date, Note, Course, Lead Source
     */
    public List<EnquiryRequestDTO> parseNewFormatCSV(MultipartFile file) throws IOException {
        log.info("Parsing NEW format CSV: {}", file.getOriginalFilename());

        List<EnquiryRequestDTO> dtos = new ArrayList<>();

        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVReader csvReader = new CSVReader(reader)) {

            List<String[]> records = csvReader.readAll();

            if (records.isEmpty()) {
                throw new IllegalArgumentException("CSV file is empty");
            }

            // Skip header row
            for (int i = 1; i < records.size(); i++) {
                String[] row = records.get(i);

                if (row.length < 5) {
                    log.warn("Skipping incomplete row {}: {}", i + 1, Arrays.toString(row));
                    continue;
                }

                try {
                    EnquiryRequestDTO dto = EnquiryRequestDTO.builder()
                            .firstName(getValueOrNull(row, 1))  // First Name
                            .middleName(getValueOrNull(row, 2))  // Middle Name
                            .lastName(getValueOrNull(row, 3))  // Last Name
                            .mobile(cleanMobileNumber(getValueOrNull(row, 4)))  // Mobile Primary
                            .secondaryMobile(cleanMobileNumber(getValueOrNull(row, 5)))  // Mobile Secondary
                            .email(getValueOrNull(row, 6))  // Email Primary
                            .currentAddress(getValueOrNull(row, 7))  // Current Address
                            .permanentAddress(getValueOrNull(row, 8))  // Permanent Address
                            .college(getValueOrNull(row, 9))  // College
                            .enquiryDate(parseDate(getValueOrNull(row, 10)))  // Enquiry Date
                            .followupDate(parseDate(getValueOrNull(row, 11)))  // Followup Date
                            .note(getValueOrNull(row, 12))  // Note
                            .courses(Arrays.asList(getValueOrNull(row, 13)))  // Course
                            .source(getValueOrNull(row, 14))  // Lead Source
                            .status("Imported")  // Default status for imports
                            .build();

                    // Validate required fields
                    if (dto.getFirstName() == null || dto.getMobile() == null ||
                            dto.getCourses() == null || dto.getSource() == null) {
                        log.warn("Skipping row {} due to missing required fields", i + 1);
                        continue;
                    }

                    dtos.add(dto);

                } catch (Exception e) {
                    log.error("Error parsing row {}: {}", i + 1, e.getMessage());
                }
            }

            log.info("Successfully parsed {} records from NEW format CSV", dtos.size());
            return dtos;

        } catch (CsvException e) {
            log.error("CSV parsing error", e);
            throw new IOException("Failed to parse CSV file: " + e.getMessage(), e);
        }
    }

    /**
     * Generate CSV export from enquiry data
     */
    public byte[] generateCSV(List<EnquiryResponseDTO> enquiries) {
        log.info("Generating CSV export for {} enquiries", enquiries.size());

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
             CSVWriter csvWriter = new CSVWriter(writer)) {

            // Write header
            String[] header = {
                    "Reg No", "First Name", "Middle Name", "Last Name", "Mobile Primary",
                    "Mobile Secondary", "Email Primary", "Current Address", "Permanent Address",
                    "College", "Qualification", "Aadhaar", "Birth Date", "Gender",
                    "Course", "Package", "Demo Lecture", "Interest Level",
                    "Enquiry Source", "Reference Name", "Enquiry Date", "Followup Date",
                    "Assigned To", "Status", "Note"
            };
            csvWriter.writeNext(header);

            // Write data rows
            for (EnquiryResponseDTO dto : enquiries) {
                String[] row = {
                        dto.getId() != null ? dto.getId().toString() : "",
                        dto.getFirstName() != null ? dto.getFirstName() : "",
                        dto.getMiddleName() != null ? dto.getMiddleName() : "",
                        dto.getLastName() != null ? dto.getLastName() : "",
                        dto.getMobile() != null ? dto.getMobile() : "",
                        dto.getSecondaryMobile() != null ? dto.getSecondaryMobile() : "",
                        dto.getEmail() != null ? dto.getEmail() : "",
                        dto.getCurrentAddress() != null ? dto.getCurrentAddress() : "",
                        dto.getPermanentAddress() != null ? dto.getPermanentAddress() : "",
                        dto.getCollege() != null ? dto.getCollege() : "",
                        dto.getQualification() != null ? dto.getQualification() : "",
                        dto.getAadhaar() != null ? dto.getAadhaar() : "",
                        dto.getBirthDate() != null ? dto.getBirthDate().toString() : "",
                        dto.getGender() != null ? dto.getGender() : "",
                        dto.getCourses() != null ? dto.getCourses() : "",
                        dto.getPackageName() != null ? dto.getPackageName() : "",
                        dto.getDemoLectureRequired() != null ? dto.getDemoLectureRequired().toString() : "",
                        dto.getInterestLevel() != null ? dto.getInterestLevel() : "",
                        dto.getSource() != null ? dto.getSource() : "",
                        dto.getReferenceName() != null ? dto.getReferenceName() : "",
                        dto.getDate() != null ? dto.getDate().toString() : "",
                        dto.getFollowupDate() != null ? dto.getFollowupDate().toString() : "",
                        dto.getAssign() != null ? dto.getAssign() : "",
                        dto.getStatus() != null ? dto.getStatus() : "",
                        dto.getNote() != null ? dto.getNote() : ""
                };
                csvWriter.writeNext(row);
            }

            csvWriter.flush();
            byte[] bytes = baos.toByteArray();
            log.info("Generated CSV with {} bytes", bytes.length);
            return bytes;

        } catch (IOException e) {
            log.error("Error generating CSV", e);
            throw new RuntimeException("Failed to generate CSV export", e);
        }
    }

    // Helper methods

    private String getValueOrNull(String[] row, int index) {
        if (index >= row.length) return null;
        String value = row[index].trim();
        return value.isEmpty() ? null : value;
    }

    private String getValueOrDefault(String[] row, int index, String defaultValue) {
        String value = getValueOrNull(row, index);
        return value != null ? value : defaultValue;
    }

    private String cleanMobileNumber(String mobile) {
        if (mobile == null) return null;
        // Remove all non-digit characters
        String cleaned = mobile.replaceAll("[^0-9]", "");
        // Remove country code if present (91 for India)
        if (cleaned.startsWith("91") && cleaned.length() == 12) {
            cleaned = cleaned.substring(2);
        }
        return cleaned.length() == 10 ? cleaned : mobile;
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }

        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(dateStr.trim(), formatter);
            } catch (DateTimeParseException ignored) {
                // Try next formatter
            }
        }

        log.warn("Unable to parse date: {}", dateStr);
        return null;
    }
}