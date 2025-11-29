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
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy")
    };

    /**
     * Parse certificates from CSV - COMPLETE FORMAT
     * Expected format: Reg No, Certificate No, Student Name, Course, Batch, Grade, Issue Date, Status
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

            log.debug("CSV Header: {}", Arrays.toString(records.get(0)));

            // Process ALL rows (skip only header)
            for (int i = 1; i < records.size(); i++) {
                String[] row = records.get(i);

                try {
                    // Expected columns:
                    // 0: Reg No
                    // 1: Certificate No
                    // 2: Student Name
                    // 3: Course
                    // 4: Batch
                    // 5: Grade
                    // 6: Issue Date
                    // 7: Status

                    String regNo = getValueAsIs(row, 0);
                    String certNo = getValueAsIs(row, 1);
                    String studentName = getValueAsIs(row, 2);
                    String courseName = getValueAsIs(row, 3);
                    String batch = getValueAsIs(row, 4);
                    String grade = getValueAsIs(row, 5);
                    LocalDate issueDate = parseDateOrNull(row, 6);
                    String status = getValueAsIs(row, 7);

                    // Default values
                    if (status == null || status.isEmpty()) {
                        status = "Not Issued";
                    }

                    Admission getMail = admissionRepository.findByRegistrationNumberAndIsDeletedFalse(regNo);
                    String studentEmail = getMail != null ? getMail.getEmailPrimary() : null;

                    CertificateCSVImportDTO dto = CertificateCSVImportDTO.builder()
                            .registrationNo(regNo)
                            .certificateNo(certNo)
                            .studentEmail(studentEmail)
                            .studentName(studentName)
                            .courseName(courseName)
                            .batch(batch)
                            .grade(grade)
                            .issueDate(issueDate)
                            .status(status)
                            .build();

                    dtos.add(dto);
                    log.debug("Row {}: {}", i, dto);

                } catch (Exception e) {
                    log.error("Row {}: Error parsing - {}", i + 1, e.getMessage());
                    // Add placeholder for failed rows
                    dtos.add(CertificateCSVImportDTO.builder()
                            .registrationNo("ROW_" + i)
                            .studentName("Parse Error: " + e.getMessage())
                            .courseName("N/A")
                            .status("Not Issued")
                            .build());
                }
            }

            log.info("✅ PARSED: {} records", dtos.size());
            return dtos;

        } catch (CsvException e) {
            log.error("CSV parsing error", e);
            throw new IOException("Failed to parse CSV file: " + e.getMessage(), e);
        }
    }

    /**
     * Generate CSV export for certificates
     */
    public byte[] generateCertificateCSV(List<CertificateDTO> certificates) {
        log.info("Generating certificate CSV export for {} records", certificates.size());

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
             CSVWriter csvWriter = new CSVWriter(writer)) {

            String[] header = {
                    "Reg No", "Certificate No", "Student Name", "Course",
                    "Batch", "Grade", "Issue Date", "Status"
            };
            csvWriter.writeNext(header);

            // Write data
            for (CertificateDTO dto : certificates) {
                String[] row = {
                        dto.getRegistrationNo() != null ? dto.getRegistrationNo() : "",
                        dto.getCertificateNo() != null ? dto.getCertificateNo() : "",
                        dto.getStudentName() != null ? dto.getStudentName() : "",
                        dto.getCourseName() != null ? dto.getCourseName() : "",
                        dto.getBatch() != null ? dto.getBatch() : "",
                        dto.getGrade() != null ? dto.getGrade() : "",
                        dto.getIssueDate() != null ? dto.getIssueDate().toString() : "",
                        dto.getStatus() != null ? dto.getStatus() : "Not Issued"
                };
                csvWriter.writeNext(row);
            }

            csvWriter.flush();
            log.info("CSV export generated successfully");
            return baos.toByteArray();

        } catch (IOException e) {
            log.error("Error generating certificate CSV", e);
            throw new RuntimeException("Failed to generate CSV export", e);
        }
    }

    /**
     * Get value AS-IS from CSV - allow null/empty
     */
    private String getValueAsIs(String[] row, int index) {
        if (index >= row.length) {
            return null;
        }
        String value = row[index];
        if (value == null || value.trim().isEmpty() ||
                value.trim().equalsIgnoreCase("NA") || value.trim().equalsIgnoreCase("N/A")) {
            return null;
        }
        // Clean course name if it has multiple courses
        if (index == 3 && value.contains("\n")) {
            String[] parts = value.split("\\n");
            return parts[0].trim();
        }
        return value.trim();
    }

    /**
     * Parse date or return NULL - no errors, no warnings
     */
    private LocalDate parseDateOrNull(String[] row, int index) {
        if (index >= row.length) {
            return null;
        }
        String dateStr = row[index];
        if (dateStr == null || dateStr.trim().isEmpty() ||
                dateStr.trim().equalsIgnoreCase("NA") || dateStr.trim().equalsIgnoreCase("N/A")) {
            return null;
        }

        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(dateStr.trim(), formatter);
            } catch (DateTimeParseException ignored) {
            }
        }

        log.warn("Could not parse date: {} at index {}", dateStr, index);
        return null;
    }
}