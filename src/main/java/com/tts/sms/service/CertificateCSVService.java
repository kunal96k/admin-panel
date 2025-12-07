package com.tts.sms.service;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvException;
import com.tts.sms.dto.CertificateCSVImportDTO;
import com.tts.sms.dto.CertificateDTO;
import com.tts.sms.model.Admission;
import com.tts.sms.repository.AdmissionRepository;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class CertificateCSVService {

    private final AdmissionRepository admissionRepository;

    private static final DateTimeFormatter[] DATE_FORMATTERS = {
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy")
    };

    /**
     * Parse certificates from CSV - AUTO-DETECTS FORMAT
     * Format 1 (WITH Course): Reg No, Certificate No, Student Name, Course, Batch, Grade, Issue Date, Status
     * Format 2 (NO Course): Reg No, Certificate No, Student Name, Batch, Grade, Issue Date, Status
     */
    public List<CertificateCSVImportDTO> parseCertificatesCSV(MultipartFile file) throws IOException {
        log.info("📥 PARSING: Certificate CSV - {}", file.getOriginalFilename());

        List<CertificateCSVImportDTO> dtos = new ArrayList<>();

        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVReader csvReader = new CSVReader(reader)) {

            List<String[]> records = csvReader.readAll();

            if (records.isEmpty()) {
                throw new IllegalArgumentException("CSV file is empty");
            }

            String[] header = records.get(0);
            log.info("📋 CSV Header: {}", Arrays.toString(header));

            //  AUTO-DETECT FORMAT
            boolean hasCourseColumn = detectFormat(header);
            log.info(" Detected format: {} Course column", hasCourseColumn ? "WITH" : "WITHOUT");

            // Process ALL rows (skip only header)
            for (int i = 1; i < records.size(); i++) {
                String[] row = records.get(i);

                try {
                    CertificateCSVImportDTO dto;

                    if (hasCourseColumn) {
                        // Format 1: WITH Course column (8 columns)
                        dto = parseRowWithCourse(row, i);
                    } else {
                        // Format 2: WITHOUT Course column (7 columns)
                        dto = parseRowWithoutCourse(row, i);
                    }

                    if (dto != null) {
                        dtos.add(dto);
                    }

                } catch (Exception e) {
                    log.error("❌ Row {}: Error parsing - {}", i + 1, e.getMessage());
                    // Add placeholder for failed rows
                    dtos.add(CertificateCSVImportDTO.builder()
                            .registrationNo("ERROR_ROW_" + i)
                            .studentName("Parse Error: " + e.getMessage())
                            .courseName("UNKNOWN")
                            .status("Not Issued")
                            .build());
                }
            }

            log.info(" PARSED: {} records", dtos.size());
            return dtos;

        } catch (CsvException e) {
            log.error("CSV parsing error", e);
            throw new IOException("Failed to parse CSV file: " + e.getMessage(), e);
        }
    }
    
    /**
     *  AUTO-DETECT CSV FORMAT by checking header
     * CSV has 7 columns: Reg No, Certificate No, Student Name, Batch (Course), Grade, Issue Date, Status
     */
    private boolean detectFormat(String[] header) {
        //  Your CSV file has 7 columns, not 8
        if (header.length == 7) {
            log.info(" Detected 7-column format (no separate course column)");
            return false; // Use parseRowWithoutCourse method
        } else if (header.length >= 8) {
            // 8+ columns = has separate Course column (old format)
            log.info(" Detected 8-column format (with course column)");
            return true;
        }

        // Default to 7-column format
        log.warn(" Unknown column count: {}, defaulting to 7-column format", header.length);
        return false;
    }

    /**
     * Parse row WITH Course column (8 columns)
     * Format: Reg No, Certificate No, Student Name, Course, Batch, Grade, Issue Date, Status
     */
    private CertificateCSVImportDTO parseRowWithCourse(String[] row, int rowIndex) {
        String regNo = getValueAsIs(row, 0);
        String certNo = getValueAsIs(row, 1);
        String studentName = getValueAsIs(row, 2);
        String courseName = getValueAsIs(row, 3); //  This is course name from "Batch" column header
        String batch = getValueAsIs(row, 4); //  Grade column (will be set to NA in DB)
        String grade = getValueAsIs(row, 5); //  Issue Date column
        LocalDate issueDate = parseDateOrNull(row, 6); //  Status column
        String rawStatus = getValueAsIs(row, 7);
        String status = normalizeStatus(rawStatus);

        log.debug("Row {}: RegNo='{}', CertNo='{}', Course='{}', Status='{}' → '{}'",
                rowIndex, regNo, certNo, courseName, rawStatus, status);

        // Fetch email from admission
        Admission admission = admissionRepository.findByRegistrationNumberAndIsDeletedFalse(regNo);
        String studentEmail = admission != null ? admission.getEmailPrimary() : null;

        return CertificateCSVImportDTO.builder()
                .registrationNo(regNo)
                .certificateNo(certNo)
                .studentEmail(studentEmail)
                .studentName(studentName)
                .courseName(courseName) //  Course name from CSV
                .batch("NA") //  Always set to NA since batch data not in CSV
                .grade(grade)
                .issueDate(issueDate)
                .status(status)
                .build();
    }

    /**
     * Parse row WITHOUT Course column (7 columns)
     * Format: Reg No, Certificate No, Student Name, Batch (Course), Grade, Issue Date, Status
     */
    private CertificateCSVImportDTO parseRowWithoutCourse(String[] row, int rowIndex) {
        //  Map CSV columns correctly for 7-column format
        String regNo = getValueAsIs(row, 0);              // Column 0: Reg No
        String certNo = getValueAsIs(row, 1);             // Column 1: Certificate No
        String studentName = getValueAsIs(row, 2);        // Column 2: Student Name
        String courseName = getValueAsIs(row, 3);         // Column 3: Batch (actually course name)
        String grade = getValueAsIs(row, 4);              // Column 4: Grade
        LocalDate issueDate = parseDateOrNull(row, 5);    // Column 5: Issue Date
        String rawStatus = getValueAsIs(row, 6);          // Column 6: Status
        String status = normalizeStatus(rawStatus);

        log.debug(" Row {}: RegNo='{}', CertNo='{}', Course='{}', Grade='{}', Status='{}' → '{}'",
                rowIndex, regNo, certNo, courseName, grade, rawStatus, status);

        // Fetch email from admission
        Admission admission = admissionRepository.findByRegistrationNumberAndIsDeletedFalse(regNo);
        String studentEmail = admission != null ? admission.getEmailPrimary() : null;

        return CertificateCSVImportDTO.builder()
                .registrationNo(regNo)
                .certificateNo(certNo)
                .studentEmail(studentEmail)
                .studentName(studentName)
                .courseName(courseName)      //  Course name from "Batch" column
                .batch("NA")                  //  Always NA - not in CSV
                .grade(grade)
                .issueDate(issueDate)
                .status(status)
                .build();
    }

    /**
     *  Normalize status value - handle all variations
     */
    private String normalizeStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            log.debug(" Empty status, defaulting to 'Not Issued'");
            return "Not Issued";
        }

        String normalized = status.trim()
                .replace("\"", "")
                .replace("'", "")
                .toLowerCase();

        log.debug(" Normalizing status: '{}' → '{}'", status, normalized);

        // Check for "Issued" variations
        if (normalized.equals("issued")) {
            log.debug(" Status: Issued");
            return "Issued";
        }

        // Check for "Not Issued" variations
        if (normalized.equals("not issued") ||
                normalized.equals("notissued") ||
                normalized.equals("pending") ||
                normalized.equals("na") ||
                normalized.equals("n/a")) {
            log.debug(" Status: Not Issued");
            return "Not Issued";
        }

        // If exact match
        if (status.trim().equals("Issued") || status.trim().equals("Not Issued")) {
            log.debug(" Status: {}", status.trim());
            return status.trim();
        }

        log.warn(" Unknown status '{}', defaulting to 'Not Issued'", status);
        return "Not Issued";
    }

    /**
     * Generate CSV export for certificates
     */
    public byte[] generateCertificateCSV(List<CertificateDTO> certificates) {
        log.info("📤 Generating certificate CSV export for {} records", certificates.size());

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
             CSVWriter csvWriter = new CSVWriter(writer)) {

            String[] header = {
                    "Reg No", "Certificate No", "Student Name", "Course",
                    "Batch", "Grade", "Issue Date", "Status"
            };
            csvWriter.writeNext(header);

            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");

            for (CertificateDTO dto : certificates) {
                String[] row = {
                        dto.getRegistrationNo() != null ? dto.getRegistrationNo() : "",
                        dto.getCertificateNo() != null ? dto.getCertificateNo() : "",
                        dto.getStudentName() != null ? dto.getStudentName() : "",
                        dto.getCourseName() != null ? dto.getCourseName() : "",
                        dto.getBatch() != null ? dto.getBatch() : "",
                        dto.getGrade() != null ? dto.getGrade() : "",
                        dto.getIssueDate() != null ? dto.getIssueDate().format(dateFormatter) : "",
                        dto.getStatus() != null ? dto.getStatus() : "Not Issued"
                };
                csvWriter.writeNext(row);
            }

            csvWriter.flush();
            log.info(" CSV export generated successfully");
            return baos.toByteArray();

        } catch (IOException e) {
            log.error("❌ Error generating certificate CSV", e);
            throw new RuntimeException("Failed to generate CSV export", e);
        }
    }

    /**
     * Get value AS-IS from CSV
     */
    private String getValueAsIs(String[] row, int index) {
        if (index >= row.length) {
            return null;
        }
        String value = row[index];

        if (value == null || value.trim().isEmpty() ||
                value.trim().equalsIgnoreCase("NA") ||
                value.trim().equalsIgnoreCase("N/A")) {
            return null;
        }

        // Clean multiline values
        if (value.contains("\n")) {
            String[] parts = value.split("\\n");
            return parts[0].trim();
        }

        return value.trim();
    }

    /**
     * Parse date or return NULL
     */
    private LocalDate parseDateOrNull(String[] row, int index) {
        if (index >= row.length) {
            return null;
        }

        String dateStr = row[index];

        if (dateStr == null || dateStr.trim().isEmpty() ||
                dateStr.trim().equalsIgnoreCase("NA") ||
                dateStr.trim().equalsIgnoreCase("N/A")) {
            return null;
        }

        // Try all date formats
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                LocalDate parsed = LocalDate.parse(dateStr.trim(), formatter);
                log.debug(" Parsed date '{}' as {}", dateStr, parsed);
                return parsed;
            } catch (DateTimeParseException ignored) {
            }
        }

        log.warn(" Could not parse date: '{}' at column {}", dateStr, index);
        return null;
    }
}