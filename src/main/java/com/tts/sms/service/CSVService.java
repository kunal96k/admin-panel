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
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy")
    };

    /**
     * LENIENT PARSING - Import ALL rows, handle missing/invalid data gracefully
     */
    public List<EnquiryRequestDTO> parseOldFormatCSV(MultipartFile file) throws IOException {
        log.info("🔄 Parsing OLD format CSV: {} (LENIENT MODE)", file.getOriginalFilename());

        List<EnquiryRequestDTO> dtos = new ArrayList<>();
        int totalRows = 0;
        int successRows = 0;
        int warningRows = 0;

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
                    // Column 0: Enquiry No. (can be null/empty)
                    String enquiryNo = getValueOrNull(row, 0);

                    // Column 1: Student Name (can be empty)
                    String fullName = getValueOrNull(row, 1);
                    if (fullName == null || fullName.trim().isEmpty()) {
                        fullName = "Unknown Student"; // Default name
                        warningRows++;
                        log.warn("⚠️ Row {}: Missing name, using default", i + 1);
                    }
                    String[] nameParts = splitName(fullName);

                    // Column 2: Mobile No. (can be invalid)
                    String rawMobile = getValueOrNull(row, 2);
                    String mobile = cleanMobileNumber(rawMobile);

                    // If mobile is still invalid, use a placeholder
                    if (mobile == null || mobile.isEmpty()) {
                        mobile = generatePlaceholderMobile(i); // Generate unique placeholder
                        warningRows++;
                        log.warn("⚠️ Row {}: Invalid mobile '{}', using placeholder '{}'",
                                i + 1, rawMobile, mobile);
                    }

                    // Column 3: Multiple Courses (can be empty)
                    String coursesStr = getValueOrNull(row, 3);
                    List<String> coursesList = parseMultipleCourses(coursesStr);

                    // If no courses found, add placeholder
                    if (coursesList.isEmpty()) {
                        coursesList.add("Not Specified");
                        warningRows++;
                        log.warn("⚠️ Row {}: No courses found, using placeholder", i + 1);
                    }

                    // Column 4: Enquiry Source (default if missing)
                    String source = getValueOrDefault(row, 4, "Unknown");

                    // Column 5: Enquiry Date (use today if invalid)
                    LocalDate enquiryDate = parseDate(getValueOrNull(row, 5));
                    if (enquiryDate == null) {
                        enquiryDate = LocalDate.now();
                        log.debug("Row {}: Using current date", i + 1);
                    }

                    // Column 6: Assign To (can be null)
                    String assignTo = getValueOrNull(row, 6);

                    // Column 7: Enquiry Status (default if missing)
                    String status = getValueOrDefault(row, 7, "New");

                    // Create DTO - ALL DATA IMPORTED
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
                            .note("Imported from CSV - Row " + (i + 1)) // Track origin
                            .build();

                    dtos.add(dto);
                    successRows++;

                } catch (Exception e) {
                    // Even if parsing fails, try to create minimal record
                    log.error("❌ Row {}: Error parsing, creating minimal record - {}",
                            i + 1, e.getMessage());

                    EnquiryRequestDTO fallbackDto = EnquiryRequestDTO.builder()
                            .name("Import Error - Row " + (i + 1))
                            .firstName("Import")
                            .lastName("Error")
                            .mobile(generatePlaceholderMobile(i))
                            .courses(List.of("Import Failed"))
                            .source("CSV Import Error")
                            .enquiryDate(LocalDate.now())
                            .status("New")
                            .note("Row " + (i + 1) + " failed to parse: " + e.getMessage())
                            .build();

                    dtos.add(fallbackDto);
                    warningRows++;
                }
            }

            log.info("✅ CSV IMPORT COMPLETE:");
            log.info("   Total Rows: {}", totalRows);
            log.info("   Successfully Parsed: {}", successRows);
            log.info("   With Warnings/Placeholders: {}", warningRows);
            log.info("   Records Created: {}", dtos.size());

            return dtos;

        } catch (CsvException e) {
            log.error("CSV parsing error", e);
            throw new IOException("Failed to parse CSV file: " + e.getMessage(), e);
        }
    }

    /**
     * NEW FORMAT - Also lenient
     */
    public List<EnquiryRequestDTO> parseNewFormatCSV(MultipartFile file) throws IOException {
        log.info("🔄 Parsing NEW format CSV: {} (LENIENT MODE)", file.getOriginalFilename());

        List<EnquiryRequestDTO> dtos = new ArrayList<>();
        int totalRows = 0;
        int warningRows = 0;

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
                    String enquiryNo = getValueOrNull(row, 0);

                    String firstName = getValueOrDefault(row, 1, "Unknown");
                    String middleName = getValueOrNull(row, 2);
                    String lastName = getValueOrDefault(row, 3, "Student");

                    String rawMobile = getValueOrNull(row, 4);
                    String mobile = cleanMobileNumber(rawMobile);
                    if (mobile == null || mobile.isEmpty()) {
                        mobile = generatePlaceholderMobile(i);
                        warningRows++;
                    }

                    String secondaryMobile = cleanMobileNumber(getValueOrNull(row, 5));
                    String email = getValueOrNull(row, 6);
                    String currentAddress = getValueOrNull(row, 7);
                    String permanentAddress = getValueOrNull(row, 8);
                    String college = getValueOrNull(row, 9);

                    LocalDate enquiryDate = parseDate(getValueOrNull(row, 10));
                    if (enquiryDate == null) enquiryDate = LocalDate.now();

                    LocalDate followupDate = parseDate(getValueOrNull(row, 11));
                    String note = getValueOrNull(row, 12);

                    String coursesStr = getValueOrNull(row, 13);
                    List<String> coursesList = parseMultipleCourses(coursesStr);
                    if (coursesList.isEmpty()) {
                        coursesList.add("Not Specified");
                        warningRows++;
                    }

                    String source = getValueOrDefault(row, 14, "Unknown");

                    EnquiryRequestDTO dto = EnquiryRequestDTO.builder()
                            .enquiryNo(enquiryNo)
                            .firstName(firstName)
                            .middleName(middleName)
                            .lastName(lastName)
                            .mobile(mobile)
                            .secondaryMobile(secondaryMobile)
                            .email(email)
                            .currentAddress(currentAddress)
                            .permanentAddress(permanentAddress)
                            .college(college)
                            .enquiryDate(enquiryDate)
                            .followupDate(followupDate)
                            .note(note)
                            .courses(coursesList)
                            .source(source)
                            .status("New")
                            .build();

                    dtos.add(dto);

                } catch (Exception e) {
                    log.error("Row {}: Error, creating fallback", i + 1);
                    warningRows++;
                    // Create minimal fallback record
                    dtos.add(createFallbackDTO(i));
                }
            }

            log.info("✅ NEW FORMAT IMPORT: {} total, {} warnings", totalRows, warningRows);
            return dtos;

        } catch (CsvException e) {
            throw new IOException("Failed to parse CSV file: " + e.getMessage(), e);
        }
    }

    /**
     * Generate unique placeholder mobile for invalid entries
     */
    private String generatePlaceholderMobile(int rowNumber) {
        // Create unique 10-digit number: 9999 + 6-digit row number
        return String.format("9999%06d", rowNumber);
    }

    /**
     * Create fallback DTO for completely failed rows
     */
    private EnquiryRequestDTO createFallbackDTO(int rowNumber) {
        return EnquiryRequestDTO.builder()
                .firstName("Import")
                .lastName("Error")
                .mobile(generatePlaceholderMobile(rowNumber))
                .courses(List.of("Import Failed"))
                .source("CSV Import Error")
                .enquiryDate(LocalDate.now())
                .status("New")
                .note("Row " + (rowNumber + 1) + " failed to parse completely")
                .build();
    }

    /**
     * Parse multiple courses - LENIENT (returns empty list if nothing found)
     */
    private List<String> parseMultipleCourses(String coursesStr) {
        List<String> courses = new ArrayList<>();

        if (coursesStr == null || coursesStr.trim().isEmpty()) {
            return courses; // Return empty, caller will add placeholder
        }

        String normalized = coursesStr.trim()
                .replaceAll("\\r\\n", "\n")
                .replaceAll("\\r", "\n");

        String[] parts = normalized.split("[,\n]");

        for (String part : parts) {
            String course = part.trim();
            if (!course.isEmpty() && !courses.contains(course)) {
                courses.add(course);
            }
        }

        return courses;
    }

    /**
     * Split name - SAFE (never returns nulls)
     */
    private String[] splitName(String fullName) {
        String[] result = new String[3];

        if (fullName == null || fullName.trim().isEmpty()) {
            result[0] = "Unknown";
            result[1] = "";
            result[2] = "Student";
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
     * Clean mobile - LENIENT (returns null if invalid, caller handles)
     */
    private String cleanMobileNumber(String mobile) {
        if (mobile == null || mobile.trim().isEmpty()) return null;

        // Remove all non-digits and spaces
        String cleaned = mobile.replaceAll("[^0-9]", "").trim();

        // Remove country code if present
        if (cleaned.startsWith("91") && cleaned.length() == 12) {
            cleaned = cleaned.substring(2);
        }

        // Return only if exactly 10 digits
        return cleaned.length() == 10 ? cleaned : null;
    }

    /**
     * Parse date - LENIENT (returns null if invalid)
     */
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

        return null; // Caller will use current date
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

    /**
     * Parse OLD FORMAT Admission CSV - LENIENT MODE
     */
    public List<com.tts.sms.dto.AdmissionRequestDTO> parseOldFormatAdmissionCSV(MultipartFile file) throws IOException {
        log.info("🔄 Parsing OLD format ADMISSION CSV: {} (LENIENT MODE)", file.getOriginalFilename());

        List<com.tts.sms.dto.AdmissionRequestDTO> dtos = new ArrayList<>();
        int totalRows = 0;
        int warningRows = 0;

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
                    // Column 0: Reg No (can be null)
                    String regNo = getValueOrNull(row, 0);

                    // Column 1: Student Name
                    String fullName = getValueOrNull(row, 1);
                    if (fullName == null || fullName.trim().isEmpty()) {
                        fullName = "Unknown Student";
                        warningRows++;
                        log.warn("⚠️ Row {}: Missing name, using default", i + 1);
                    }
                    String[] nameParts = splitName(fullName);

                    // Column 2: Mobile No
                    String rawMobile = getValueOrNull(row, 2);
                    String mobile = cleanMobileNumber(rawMobile);
                    if (mobile == null || mobile.isEmpty()) {
                        mobile = generatePlaceholderMobile(i);
                        warningRows++;
                        log.warn("⚠️ Row {}: Invalid mobile '{}', using placeholder '{}'",
                                i + 1, rawMobile, mobile);
                    }

                    // Column 3: Course(s) - Can be comma-separated
                    String coursesStr = getValueOrNull(row, 3);
                    List<String> coursesList = parseMultipleCourses(coursesStr);
                    if (coursesList.isEmpty()) {
                        coursesList.add("Not Specified");
                        warningRows++;
                        log.warn("⚠️ Row {}: No courses found, using placeholder", i + 1);
                    }

                    // Column 4: Admission Date
                    LocalDate admissionDate = parseDate(getValueOrNull(row, 4));
                    if (admissionDate == null) {
                        admissionDate = LocalDate.now();
                        log.debug("Row {}: Using current date", i + 1);
                    }

                    com.tts.sms.dto.AdmissionRequestDTO dto = com.tts.sms.dto.AdmissionRequestDTO.builder()
                            .firstName(nameParts[0])
                            .middleName(nameParts[1])
                            .lastName(nameParts[2])
                            .mobilePrimary(mobile)
                            .courses(coursesList)
                            .admissionDate(admissionDate)
                            .leadSource("CSV_IMPORT")
                            .documentType("Aadhaar Card") // Default
                            .academicYear(String.valueOf(java.time.Year.now().getValue()))
                            .build();

                    dtos.add(dto);

                } catch (Exception e) {
                    log.error("❌ Row {}: Error parsing - {}", i + 1, e.getMessage());
                    warningRows++;

                    // Create fallback admission DTO
                    com.tts.sms.dto.AdmissionRequestDTO fallbackDto = com.tts.sms.dto.AdmissionRequestDTO.builder()
                            .firstName("Import")
                            .lastName("Error")
                            .mobilePrimary(generatePlaceholderMobile(i))
                            .courses(List.of("Import Failed"))
                            .admissionDate(LocalDate.now())
                            .leadSource("CSV_IMPORT_ERROR")
                            .documentType("Aadhaar Card")
                            .academicYear(String.valueOf(java.time.Year.now().getValue()))
                            .build();

                    dtos.add(fallbackDto);
                }
            }

            log.info("✅ ADMISSION CSV IMPORT COMPLETE:");
            log.info("   Total Rows: {}", totalRows);
            log.info("   With Warnings: {}", warningRows);
            log.info("   Records Created: {}", dtos.size());

            return dtos;

        } catch (CsvException e) {
            log.error("CSV parsing error", e);
            throw new IOException("Failed to parse CSV file: " + e.getMessage(), e);
        }
    }

    /**
     * Export CSV (unchanged)
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
}