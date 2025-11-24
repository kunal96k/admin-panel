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
                DateTimeFormatter.ofPattern("d/M/yyyy"),
                DateTimeFormatter.ofPattern("d-M-yyyy")
        };

        /**
         * Parse OLD FORMAT CSV - FIXED for multiple courses
         * CSV Format: Enquiry No., Student Name, Mobile No., Course(s), Enquiry Source,
         *             Enquiry Date, Assign To, Enquiry Status
         *
         * ⚠️ CRITICAL: Enquiry No. from CSV is IGNORED (database auto-generates ID)
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

                log.debug("CSV Header: {}", Arrays.toString(records.get(0)));

                // Skip header row (index 0)
                for (int i = 1; i < records.size(); i++) {
                    String[] row = records.get(i);

                    if (row.length < 4) {
                        log.warn("Skipping incomplete row {}: {}", i + 1, Arrays.toString(row));
                        continue;
                    }

                    try {
                        //  Column 0: Enquiry No. - IGNORED (database auto-generates)
                        // String enquiryNo = getValueOrNull(row, 0); // NOT USED

                        // Column 1: Student Name
                        String fullName = getValueOrNull(row, 1);
                        String[] nameParts = splitName(fullName);

                        // Column 2: Mobile No.
                        String mobile = cleanMobileNumber(getValueOrNull(row, 2));

                        // ✅ Column 3: Multiple Courses - CRITICAL FIX
                        String coursesStr = getValueOrNull(row, 3);
                        List<String> coursesList = parseMultipleCourses(coursesStr);

                        // Validation
                        if (coursesList.isEmpty() || mobile == null) {
                            log.warn("Row {} - Missing required fields (mobile or courses)", i + 1);
                            continue;
                        }

                        log.info("Row {}: name={}, mobile={}, courses={} (count: {})",
                                i + 1, fullName, mobile, coursesList, coursesList.size());

                        // Column 4: Enquiry Source
                        String source = getValueOrDefault(row, 4, "Unknown");

                        // Column 5: Enquiry Date
                        LocalDate enquiryDate = parseDate(getValueOrNull(row, 5));
                        if (enquiryDate == null) {
                            enquiryDate = LocalDate.now(); // Default to today
                        }

                        // Column 6: Assign To
                        String assignTo = getValueOrNull(row, 6);

                        // Column 7: Enquiry Status
                        String status = getValueOrDefault(row, 7, "New");

                        EnquiryRequestDTO dto = EnquiryRequestDTO.builder()
                                // ⚠️ DO NOT set enquiryNo - database auto-generates ID
                                .name(fullName)
                                .firstName(nameParts[0])
                                .middleName(nameParts[1])
                                .lastName(nameParts[2])
                                .mobile(mobile)
                                .courses(coursesList)  // ✅ ALL courses preserved
                                .source(source)
                                .enquiryDate(enquiryDate)
                                .assignTo(assignTo)
                                .status(status)
                                .build();

                        dtos.add(dto);

                    } catch (Exception e) {
                        log.error("Error parsing row {}: {}", i + 1, e.getMessage(), e);
                    }
                }

                log.info("✅ Successfully parsed {} records from OLD format CSV", dtos.size());
                return dtos;

            } catch (CsvException e) {
                log.error("CSV parsing error", e);
                throw new IOException("Failed to parse CSV file: " + e.getMessage(), e);
            }
        }

        /**
         * 🔥 CRITICAL FIX: Parse MULTIPLE courses from CSV cell
         *
         * Handles formats:
         * 1. Single course: "PYTHON"
         * 2. Comma-separated: "REDHAT (RHCSA & RHCE), CISCO CERTIFIED NETWORK ASSOCIATE, AMAZON WEB SERVICES"
         * 3. Newline-separated: "PYTHON\nJAVA\nREACT JS"
         * 4. Mixed: "PYTHON, JAVA\nREACT JS"
         */
        private List<String> parseMultipleCourses(String coursesStr) {
            List<String> courses = new ArrayList<>();

            if (coursesStr == null || coursesStr.trim().isEmpty()) {
                log.warn("Empty courses string");
                return courses;
            }

            // Step 1: Normalize line breaks
            String normalized = coursesStr.trim()
                    .replaceAll("\\r\\n", "\n")
                    .replaceAll("\\r", "\n");

            // Step 2: Split by BOTH comma AND newline
            String[] parts = normalized.split("[,\n]");

            // Step 3: Clean and deduplicate
            for (String part : parts) {
                String course = part.trim();

                // Skip empty strings
                if (course.isEmpty()) {
                    continue;
                }

                // ✅ Add if not already in list (avoid duplicates)
                if (!courses.contains(course)) {
                    courses.add(course);
                    log.debug("  Added course: {}", course);
                }
            }

            log.info("Parsed {} courses from '{}': {}", courses.size(), coursesStr, courses);
            return courses;
        }

        /**
         * Parse NEW FORMAT CSV
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

                for (int i = 1; i < records.size(); i++) {
                    String[] row = records.get(i);

                    if (row.length < 5) {
                        log.warn("Skipping incomplete row {}: {}", i + 1, Arrays.toString(row));
                        continue;
                    }

                    try {
                        //  Column 0: Enquiry No. - IGNORED

                        String firstName = getValueOrNull(row, 1);
                        String middleName = getValueOrNull(row, 2);
                        String lastName = getValueOrNull(row, 3);
                        String mobile = cleanMobileNumber(getValueOrNull(row, 4));
                        String secondaryMobile = cleanMobileNumber(getValueOrNull(row, 5));
                        String email = getValueOrNull(row, 6);
                        String currentAddress = getValueOrNull(row, 7);
                        String permanentAddress = getValueOrNull(row, 8);
                        String college = getValueOrNull(row, 9);

                        LocalDate enquiryDate = parseDate(getValueOrNull(row, 10));
                        if (enquiryDate == null) enquiryDate = LocalDate.now();

                        LocalDate followupDate = parseDate(getValueOrNull(row, 11));
                        String note = getValueOrNull(row, 12);

                        // ✅ Column 13: Multiple Courses
                        String coursesStr = getValueOrNull(row, 13);
                        List<String> coursesList = parseMultipleCourses(coursesStr);

                        String source = getValueOrDefault(row, 14, "Unknown");

                        if (coursesList.isEmpty() || mobile == null) {
                            log.warn("Row {} - Missing required fields", i + 1);
                            continue;
                        }

                        EnquiryRequestDTO dto = EnquiryRequestDTO.builder()
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
                        log.error("Error parsing row {}: {}", i + 1, e.getMessage());
                    }
                }

                log.info("✅ Successfully parsed {} records from NEW format CSV", dtos.size());
                return dtos;

            } catch (CsvException e) {
                throw new IOException("Failed to parse CSV file: " + e.getMessage(), e);
            }
        }

        /**
         * Split full name into first, middle, last
         */
        private String[] splitName(String fullName) {
            String[] result = new String[3];

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
         * Generate CSV export - Fixed to NOT include database ID
         */
        public byte[] generateCSV(List<EnquiryResponseDTO> enquiries) {
            log.info("Generating CSV export for {} enquiries", enquiries.size());

            try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                 OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
                 CSVWriter csvWriter = new CSVWriter(writer)) {

                // Header - NO Enquiry No column (database ID should not be exported)
                String[] header = {
                        "First Name", "Middle Name", "Last Name", "Mobile Primary",
                        "Mobile Secondary", "Email Primary", "Current Address", "Permanent Address",
                        "College", "Qualification", "Aadhaar", "Birth Date", "Gender",
                        "Course(s)", "Package", "Demo Lecture", "Interest Level",
                        "Enquiry Source", "Reference Name", "Enquiry Date", "Followup Date",
                        "Assigned To", "Status", "Note"
                };
                csvWriter.writeNext(header);

                // Write data rows
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
                            dto.getCourses() != null ? dto.getCourses() : "", // Comma-separated
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