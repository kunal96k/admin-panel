package com.tts.sms.service;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvException;
import com.tts.sms.dto.AdmissionRequestDTO;
import com.tts.sms.dto.EnquiryRequestDTO;
import com.tts.sms.dto.EnquiryResponseDTO;
import com.tts.sms.dto.FeesCSVImportDTO;
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

import static java.lang.Double.parseDouble;

@Slf4j
@Service
public class CSVService {

    private static final DateTimeFormatter[] DATE_FORMATTERS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy")
    };

    /**
     * STRICT MODE: Parse OLD format CSV with ZERO modification
     * - Empty/null -> "N/A"
     * - Keep exact CSV values
     * - No validation, no cleanup
     */
    public List<EnquiryRequestDTO> parseOldFormatCSV(MultipartFile file) throws IOException {
        log.info("[IMPORT] STRICT PARSING - OLD FORMAT: {}", file.getOriginalFilename());

        List<EnquiryRequestDTO> dtos = new ArrayList<>();
        int totalRows = 0;

        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVReader csvReader = new CSVReader(reader)) {

            List<String[]> records = csvReader.readAll();

            if (records.isEmpty()) {
                throw new IllegalArgumentException("CSV file is empty");
            }

            log.debug("CSV Header: {}", Arrays.toString(records.get(0)));

            // Process ALL rows (skip only header)
            for (int i = 1; i < records.size(); i++) {
                totalRows++;
                String[] row = records.get(i);

                try {
                    // Column 0: Enquiry No.
                    String enquiryNo = getOriginalValue(row, 0);

                    // Column 1: Student Name
                    String fullName = getOriginalValue(row, 1);
                    String[] nameParts = splitNameExact(fullName);

                    // Column 2: Mobile
                    String mobile = getOriginalValue(row, 2);

                    // Column 3: Courses
                    String coursesStr = getOriginalValue(row, 3);
                    List<String> coursesList = parseCoursesExact(coursesStr);

                    // Column 4: Source
                    String source = getOriginalValue(row, 4);

                    // Column 5: Enquiry Date
                    LocalDate enquiryDate = parseDate(getOriginalValue(row, 5));

                    // Column 6: Assign To
                    String assignTo = getOriginalValue(row, 6);

                    // Column 7: Status
                    String status = getOriginalValue(row, 7);

                    EnquiryRequestDTO dto = EnquiryRequestDTO.builder()
                            .enquiryNo(enquiryNo)
                            .name(fullName)
                            .firstName(nameParts[0])
                            .middleName(nameParts[1])
                            .lastName(nameParts[2])
                            .mobile(mobile)
                            .courses(coursesList)
                            .source(source)
                            .enquiryDate(enquiryDate)
                            .assignTo(assignTo)
                            .status(status)
                            .build();

                    dtos.add(dto);

                } catch (Exception e) {
                    log.error("[FAIL] Row {}: Error parsing - {}", i + 1, e.getMessage());
                    // Create minimal fallback
                    dtos.add(createFallbackEnquiryDTO(i));
                }
            }

            log.info(" Parsed {} records (STRICT MODE)", dtos.size());
            return dtos;

        } catch (CsvException e) {
            log.error("CSV parsing error", e);
            throw new IOException("Failed to parse CSV file: " + e.getMessage(), e);
        }
    }

    /**
     * STRICT MODE: Parse NEW format CSV with ZERO modification
     */
    public List<EnquiryRequestDTO> parseNewFormatCSV(MultipartFile file) throws IOException {
        log.info("[IMPORT] STRICT PARSING - NEW FORMAT: {}", file.getOriginalFilename());

        List<EnquiryRequestDTO> dtos = new ArrayList<>();
        int totalRows = 0;

        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVReader csvReader = new CSVReader(reader)) {

            List<String[]> records = csvReader.readAll();

            if (records.isEmpty()) {
                throw new IllegalArgumentException("CSV file is empty");
            }

            for (int i = 1; i < records.size(); i++) {
                totalRows++;
                String[] row = records.get(i);

                try {
                    EnquiryRequestDTO dto = EnquiryRequestDTO.builder()
                            .enquiryNo(getOriginalValue(row, 0))
                            .firstName(getOriginalValue(row, 1))
                            .middleName(getOriginalValue(row, 2))
                            .lastName(getOriginalValue(row, 3))
                            .mobile(getOriginalValue(row, 4))
                            .secondaryMobile(getOriginalValue(row, 5))
                            .email(getOriginalValue(row, 6))
                            .currentAddress(getOriginalValue(row, 7))
                            .permanentAddress(getOriginalValue(row, 8))
                            .college(getOriginalValue(row, 9))
                            .enquiryDate(parseDate(getOriginalValue(row, 10)))
                            .followupDate(parseDate(getOriginalValue(row, 11)))
                            .note(getOriginalValue(row, 12))
                            .courses(parseCoursesExact(getOriginalValue(row, 13)))
                            .source(getOriginalValue(row, 14))
                            .status("N/A")
                            .build();

                    dtos.add(dto);

                } catch (Exception e) {
                    log.error("Row {}: Error, creating fallback", i + 1);
                    dtos.add(createFallbackEnquiryDTO(i));
                }
            }

            log.info(" Parsed {} records (STRICT MODE)", dtos.size());
            return dtos;

        } catch (CsvException e) {
            throw new IOException("Failed to parse CSV file: " + e.getMessage(), e);
        }
    }

    /**
     * Counselor CSV Format: DATE, STUDENT NAME, SOURCE, CONTACT NO, COURSE, COUNSELOR NAME
     */
    public List<EnquiryRequestDTO> parseCounselorFormatCSV(MultipartFile file) throws IOException {
        log.info("[IMPORT] STRICT PARSING - COUNSELOR FORMAT: {}", file.getOriginalFilename());

        List<EnquiryRequestDTO> dtos = new ArrayList<>();
        int totalRows = 0;

        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVReader csvReader = new CSVReader(reader)) {

            List<String[]> records = csvReader.readAll();

            if (records.isEmpty()) {
                throw new IllegalArgumentException("CSV file is empty");
            }

            log.debug("CSV Header: {}", Arrays.toString(records.get(0)));

            for (int i = 1; i < records.size(); i++) {
                totalRows++;
                String[] row = records.get(i);

                try {
                    // Column 0: DATE
                    LocalDate enquiryDate = parseDate(getOriginalValue(row, 0));
                    if (enquiryDate == null) {
                        enquiryDate = LocalDate.now();
                    }

                    // Column 1: STUDENT NAME
                    String studentName = getOriginalValue(row, 1);
                    if (studentName.equalsIgnoreCase("N/A")) {
                        studentName = "";
                    }
                    String[] nameParts = splitNameExact(studentName);

                    // Column 2: SOURCE
                    String source = getOriginalValue(row, 2);

                    // Column 3: CONTACT NO
                    String mobile = getOriginalValue(row, 3);
                    if (mobile == null || mobile.equals("N/A")) {
                        mobile = "";
                    }

                    // Column 4: COURSE
                    String courseStr = getOriginalValue(row, 4);
                    List<String> coursesList = parseCoursesExact(courseStr);

                    // Column 5: COUNSELOR NAME
                    String counselorName = getOriginalValue(row, 5);

                    EnquiryRequestDTO dto = EnquiryRequestDTO.builder()
                            .enquiryDate(enquiryDate)
                            .name(studentName)
                            .firstName(nameParts[0])
                            .middleName(nameParts[1])
                            .lastName(nameParts[2])
                            .source(source)
                            .mobile(mobile)
                            .courses(coursesList)
                            .assignTo(counselorName)
                            .status("New")
                            .build();

                    dtos.add(dto);

                } catch (Exception e) {
                    log.error("[FAIL] Row {}: Error parsing counselor format - {}", i + 1, e.getMessage());
                    dtos.add(createFallbackEnquiryDTO(i));
                }
            }

            log.info(" Parsed {} records (COUNSELOR FORMAT)", dtos.size());
            return dtos;

        } catch (CsvException e) {
            log.error("CSV parsing error", e);
            throw new IOException("Failed to parse CSV file: " + e.getMessage(), e);
        }
    }

    /**
     * Parse OLD FORMAT Admission CSV - STRICT MODE
     */
    public List<AdmissionRequestDTO> parseOldFormatAdmissionCSV(MultipartFile file) throws IOException {
        log.info("[IMPORT] STRICT PARSING - ADMISSION CSV: {}", file.getOriginalFilename());

        List<AdmissionRequestDTO> dtos = new ArrayList<>();
        int totalRows = 0;

        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVReader csvReader = new CSVReader(reader)) {

            List<String[]> records = csvReader.readAll();

            if (records.isEmpty()) {
                throw new IllegalArgumentException("CSV file is empty");
            }

            log.debug("CSV Header: {}", Arrays.toString(records.get(0)));

            for (int i = 1; i < records.size(); i++) {
                totalRows++;
                String[] row = records.get(i);

                try {
                    // Column 0: Registration No
                    String regNo = getOriginalValue(row, 0);

                    // Column 1: Student Name
                    String fullName = getOriginalValue(row, 1);
                    String[] nameParts = splitNameExact(fullName);

                    // Column 2: Mobile
                    String mobile = getOriginalValue(row, 2);

                    // Column 3: Courses
                    String coursesStr = getOriginalValue(row, 3);
                    List<String> coursesList = parseCoursesExact(coursesStr);

                    // Column 4: Admission Date
                    LocalDate admissionDate = parseDate(getOriginalValue(row, 4));

                    AdmissionRequestDTO dto = AdmissionRequestDTO.builder()
                            .registrationNumber(regNo)
                            .firstName(nameParts[0])
                            .middleName(nameParts[1])
                            .lastName(nameParts[2])
                            .mobilePrimary(mobile)
                            .courses(coursesList)
                            .admissionDate(admissionDate != null ? admissionDate : LocalDate.now())
                            .leadSource("CSV_IMPORT")
                            .documentType("Aadhaar Card")
                            .academicYear(String.valueOf(java.time.Year.now().getValue()))
                            .build();

                    dtos.add(dto);

                } catch (Exception e) {
                    log.error("[FAIL] Row {}: Error parsing - {}", i + 1, e.getMessage());
                    dtos.add(createFallbackAdmissionDTO(i));
                }
            }

            log.info(" ADMISSION CSV IMPORT COMPLETE: {} records created", dtos.size());
            return dtos;

        } catch (CsvException e) {
            log.error("CSV parsing error", e);
            throw new IOException("Failed to parse CSV file: " + e.getMessage(), e);
        }
    }

    /**
     * Export Enquiries to CSV
     */
    public byte[] generateCSV(List<EnquiryResponseDTO> enquiries) {
        log.info("Generating CSV export for {} enquiries", enquiries.size());

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
             CSVWriter csvWriter = new CSVWriter(writer)) {

            String[] header = {
                    "First Name", "Middle Name", "Last Name", "Mobile Primary",
                    "Mobile Secondary", "Email Primary", "Current Address", "Permanent Address",
                    "College", "Qualification", "Aadhaar", "Birth Date", "Gender",
                    "Course(s)", "Package", "Demo Lecture", "Interest Level",
                    "Enquiry Source", "Reference Name", "Enquiry Date", "Followup Date",
                    "Assigned To", "Status", "Note"
            };
            csvWriter.writeNext(header);

            for (EnquiryResponseDTO dto : enquiries) {
                String[] row = {
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

    // ==================== HELPER METHODS ====================

    /**
     * Get original value or "N/A" - NO MODIFICATION
     */
    private String getOriginalValue(String[] row, int index) {
        if (index >= row.length) return "N/A";
        String value = row[index];
        return (value == null || value.trim().isEmpty()) ? "N/A" : value.trim();
    }

    /**
     * Parse courses - Keep exact values
     */
    private List<String> parseCoursesExact(String coursesStr) {
        List<String> courses = new ArrayList<>();

        if (coursesStr == null || coursesStr.equals("N/A")) {
            courses.add("N/A");
            return courses;
        }

        String[] parts = coursesStr.split("[,\\n]");
        for (String part : parts) {
            String course = part.trim();
            if (!course.isEmpty()) {
                courses.add(course);
            }
        }

        return courses.isEmpty() ? List.of("N/A") : courses;
    }

    /**
     * Split name - Keep exact parts
     */
    private String[] splitNameExact(String fullName) {
        String[] result = new String[3];

        if (fullName == null || fullName.equals("N/A")) {
            result[0] = "N/A";
            result[1] = "N/A";
            result[2] = "N/A";
            return result;
        }

        String[] parts = fullName.trim().split("\\s+");

        if (parts.length == 1) {
            result[0] = parts[0];
            result[1] = "N/A";
            result[2] = "N/A";
        } else if (parts.length == 2) {
            result[0] = parts[0];
            result[1] = "N/A";
            result[2] = parts[1];
        } else {
            result[0] = parts[0];
            result[1] = String.join(" ", Arrays.copyOfRange(parts, 1, parts.length - 1));
            result[2] = parts[parts.length - 1];
        }

        return result;
    }

    /**
     * Parse date - Return null if invalid
     */
    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.equals("N/A")) {
            return null;
        }

        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(dateStr, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }

        return null;
    }

    /**
     * Create fallback enquiry DTO for failed rows
     */
    private EnquiryRequestDTO createFallbackEnquiryDTO(int rowNumber) {
        return EnquiryRequestDTO.builder()
                .firstName("Import")
                .lastName("Error")
                .mobile("N/A")
                .courses(List.of("N/A"))
                .source("CSV_IMPORT_ERROR")
                .enquiryDate(LocalDate.now())
                .status("New")
                .note("Row " + (rowNumber + 1) + " failed to parse")
                .build();
    }

    /**
     * Create fallback admission DTO for failed rows
     */
    private AdmissionRequestDTO createFallbackAdmissionDTO(int rowNumber) {
        return AdmissionRequestDTO.builder()
                .firstName("Import")
                .lastName("Error")
                .mobilePrimary("N/A")
                .courses(List.of("N/A"))
                .admissionDate(LocalDate.now())
                .leadSource("CSV_IMPORT_ERROR")
                .documentType("Aadhaar Card")
                .academicYear(String.valueOf(java.time.Year.now().getValue()))
                .build();
    }

    /**
     * Parse FEES CSV - PRESERVE NULL/EMPTY VALUES
     * Format: Reg No, Student Name, Mobile, Total Fees, Fees Due, Total Paid, Due Date, Fees Refund, Status, Course
     */
    public List<FeesCSVImportDTO> parseFeesCSV(MultipartFile file) throws IOException {
        log.info("[IMPORT] PARSING FEES CSV (PRESERVE NULLS): {}", file.getOriginalFilename());

        List<FeesCSVImportDTO> dtos = new ArrayList<>();

        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVReader csvReader = new CSVReader(reader)) {

            List<String[]> records = csvReader.readAll();

            if (records.isEmpty()) {
                throw new IllegalArgumentException("CSV file is empty");
            }

            log.debug("CSV Header: {}", Arrays.toString(records.get(0)));

            // Process ALL rows (skip only header)
            for (int i = 1; i < records.size(); i++) {
                String[] row = records.get(i);

                try {
                    FeesCSVImportDTO dto = FeesCSVImportDTO.builder()
                            .registrationNumber(getStringOrNull(row, 0))
                            .studentName(getStringOrNull(row, 1))
                            .mobile(getStringOrNull(row, 2))
                            .totalFees(parseDoubleOrNull(row, 3))
                            .feesDue(parseDoubleOrNull(row, 4))
                            .totalPaid(parseDoubleOrNull(row, 5))
                            .dueDate(parseDateOrNull(row, 6)) // KEEP NULL IF EMPTY
                            .feesRefund(parseDoubleOrNull(row, 7))
                            .status(getStringOrNull(row, 8))
                            .course(getStringOrNull(row, 9))
                            .build();

                    dtos.add(dto);

                } catch (Exception e) {
                    log.error("[FAIL] Row {}: Error parsing - {}", i + 1, e.getMessage());
                    dtos.add(createFallbackFeesDTO(i));
                }
            }

            log.info(" Parsed {} fees records (NULL-SAFE MODE)", dtos.size());
            return dtos;

        } catch (CsvException e) {
            throw new IOException("Failed to parse CSV file: " + e.getMessage(), e);
        }
    }

    // ==================== HELPER METHODS - NULL SAFE ====================

    /**
     * Get string value or null (NOT "N/A")
     */
    private String getStringOrNull(String[] row, int index) {
        if (index >= row.length) return null;
        String value = row[index];
        if (value == null || value.trim().isEmpty() || value.trim().equalsIgnoreCase("N/A")) {
            return null;
        }
        return value.trim();
    }

    /**
     * Parse double or return null
     */
    private Double parseDoubleOrNull(String[] row, int index) {
        String value = getStringOrNull(row, index);
        if (value == null) return null;

        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid number at row[{}]: {}", index, value);
            return null;
        }
    }

    /**
     * Parse date or return NULL (DO NOT SET CURRENT DATE)
     */
    private LocalDate parseDateOrNull(String[] row, int index) {
        String dateStr = getStringOrNull(row, index);
        if (dateStr == null) {
            return null; // KEEP NULL - DO NOT SET CURRENT DATE
        }

        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(dateStr, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }

        log.warn("Invalid date format at row[{}]: {}", index, dateStr);
        return null; // KEEP NULL ON PARSE ERROR
    }

    private FeesCSVImportDTO createFallbackFeesDTO(int rowNumber) {
        return FeesCSVImportDTO.builder()
                .registrationNumber("ERROR_ROW_" + (rowNumber + 1))
                .studentName("Import Error")
                .mobile(null)
                .totalFees(null)
                .feesDue(null)
                .totalPaid(null)
                .dueDate(null)
                .feesRefund(null)
                .status("Error")
                .course(null)
                .build();
    }

    // Legacy helper methods (for backward compatibility)
    private String getValueOrNull(String[] row, int index) {
        return getOriginalValue(row, index);
    }

    private String getValueOrDefault(String[] row, int index, String defaultValue) {
        String value = getOriginalValue(row, index);
        return value.equals("N/A") ? defaultValue : value;
    }

    private String generatePlaceholderMobile(int rowNumber) {
        return String.format("9999%06d", rowNumber);
    }

    private String cleanMobileNumber(String mobile) {
        if (mobile == null || mobile.trim().isEmpty()) return "N/A";
        String cleaned = mobile.replaceAll("\\s+", "").trim();
        if (cleaned.startsWith("91") && cleaned.length() == 12) {
            cleaned = cleaned.substring(2);
        }
        return cleaned.isEmpty() ? "N/A" : cleaned;
    }

    private List<String> parseMultipleCourses(String coursesStr) {
        return parseCoursesExact(coursesStr);
    }

    private String[] splitName(String fullName) {
        return splitNameExact(fullName);
    }
}