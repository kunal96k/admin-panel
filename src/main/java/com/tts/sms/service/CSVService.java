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
import java.util.stream.Collectors;

@Slf4j
@Service
public class CSVService {

    private static final DateTimeFormatter[] DATE_FORMATTERS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy")
    };

    /**
     * Parse OLD FORMAT CSV - FIXED for multiple courses
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
                    String enquiryNo = getValueOrNull(row, 0); // Column 0: Enquiry No
                    String fullName = getValueOrNull(row, 1);  // Column 1: Student Name
                    String mobile = getValueOrNull(row, 2);     // Column 2: Mobile No
                    String coursesStr = getValueOrNull(row, 3); // Column 3: Course (multiple)

                    String[] nameParts = splitName(fullName);

                    // **FIXED: Parse MULTIPLE courses separated by comma**
                    List<String> coursesList = parseMultipleCourses(coursesStr);

                    if (coursesList.isEmpty() || mobile == null) {
                        log.warn("Skipping row {} - missing required fields", i + 1);
                        continue;
                    }

                    log.debug("Row {}: enquiryNo={}, name={}, mobile={}, courses={}",
                            i + 1, enquiryNo, fullName, mobile, coursesList);

                    EnquiryRequestDTO dto = EnquiryRequestDTO.builder()
                            .enquiryNo(enquiryNo)
                            .name(fullName)
                            .firstName(nameParts[0])
                            .middleName(nameParts[1])
                            .lastName(nameParts[2])
                            .mobile(cleanMobileNumber(mobile))
                            .courses(coursesList)  // Multiple courses
                            .source(getValueOrDefault(row, 4, "Unknown"))
                            .enquiryDate(parseDate(getValueOrNull(row, 5)))
                            .assignTo(getValueOrNull(row, 6))
                            .status(getValueOrDefault(row, 7, "New"))
                            .build();

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
     * Parse NEW FORMAT CSV - FIXED for multiple courses
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
                    String coursesStr = getValueOrNull(row, 13); // Column 13: Course (multiple)
                    List<String> coursesList = parseMultipleCourses(coursesStr);

                    if (coursesList.isEmpty()) {
                        log.warn("Skipping row {} - no valid courses found", i + 1);
                        continue;
                    }

                    EnquiryRequestDTO dto = EnquiryRequestDTO.builder()
                            .enquiryNo(getValueOrNull(row, 0))  // Import enquiry number
                            .firstName(getValueOrNull(row, 1))
                            .middleName(getValueOrNull(row, 2))
                            .lastName(getValueOrNull(row, 3))
                            .mobile(cleanMobileNumber(getValueOrNull(row, 4)))
                            .secondaryMobile(cleanMobileNumber(getValueOrNull(row, 5)))
                            .email(getValueOrNull(row, 6))
                            .currentAddress(getValueOrNull(row, 7))
                            .permanentAddress(getValueOrNull(row, 8))
                            .college(getValueOrNull(row, 9))
                            .enquiryDate(parseDate(getValueOrNull(row, 10)))
                            .followupDate(parseDate(getValueOrNull(row, 11)))
                            .note(getValueOrNull(row, 12))
                            .courses(coursesList)  // Multiple courses
                            .source(getValueOrDefault(row, 14, "Unknown"))
                            .status("New")
                            .build();

                    if (dto.getMobile() == null) {
                        log.warn("Skipping row {} due to missing mobile", i + 1);
                        continue;
                    }

                    dtos.add(dto);
                    log.debug("Parsed row {}: {} {} - courses: {}",
                            i + 1, dto.getFirstName(), dto.getLastName(), coursesList);

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
     * **FIXED: Parse MULTIPLE courses separated by comma**
     * Supports formats like: "PYTHON, JAVA, C++" or "PYTHON,JAVA" or "PYTHON"
     */
    private List<String> parseMultipleCourses(String coursesStr) {
        List<String> courses = new ArrayList<>();

        if (coursesStr == null || coursesStr.trim().isEmpty()) {
            return courses;
        }

        // Split by comma and clean each course
        String[] courseParts = coursesStr.split(",");

        for (String course : courseParts) {
            String cleanedCourse = course.trim()
                    .replaceAll("\\s+", " ")
                    .replaceAll("[\\r\\n]+", " ");

            if (!cleanedCourse.isEmpty()) {
                courses.add(cleanedCourse);
            }
        }

        return courses;
    }

    /**
     * Split full name into first, middle, last
     */
    private String[] splitName(String fullName) {
        String[] result = new String[3]; // [first, middle, last]

        if (fullName == null || fullName.trim().isEmpty()) {
            result[0] = "";
            result[1] = "";
            result[2] = "";
            return result;
        }

        String[] parts = fullName.trim().split("\\s+");

        if (parts.length == 1) {
            result[0] = parts[0];
            result[1] = "";
            result[2] = "";
        } else if (parts.length == 2) {
            result[0] = parts[0];
            result[1] = "";
            result[2] = parts[1];
        } else {
            result[0] = parts[0];
            result[1] = String.join(" ", Arrays.copyOfRange(parts, 1, parts.length - 1));
            result[2] = parts[parts.length - 1];
        }

        return result;
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
                    "Enquiry No", "First Name", "Middle Name", "Last Name", "Mobile Primary",
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
            return baos.toByteArray();

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
        String cleaned = mobile.replaceAll("[^0-9]", "");
        if (cleaned.startsWith("91") && cleaned.length() == 12) {
            cleaned = cleaned.substring(2);
        }
        return cleaned.length() == 10 ? cleaned : null;
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }

        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(dateStr.trim(), formatter);
            } catch (DateTimeParseException ignored) {
            }
        }

        log.warn("Unable to parse date: {}, returning null", dateStr);
        return null;
    }
}