package com.tts.sms.service;

import com.tts.sms.dto.AttendanceBatchSyncRequestDTO;
import com.tts.sms.dto.AttendanceStudentSyncDTO;
import com.tts.sms.dto.AttendanceSyncResponseDTO;
import com.tts.sms.model.Admission;
import com.tts.sms.repository.AdmissionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AttendanceSyncService {

    private final AdmissionRepository admissionRepository;
    private final RestTemplate restTemplate;

    @Value("${app.attendance-sync.enabled:true}")
    private boolean attendanceSyncEnabled;

    @Value("${app.attendance-sync.server-url:https://attendance.ttsnasik.com}")
    private String serverUrl;

    @Value("${app.attendance-sync.api-key:TTS_ATTENDANCE_SECRET_KEY_2026}")
    private String apiKey;

    public AttendanceSyncService(AdmissionRepository admissionRepository,
                                 @Value("${app.attendance-sync.connect-timeout-ms:5000}") int connectTimeout,
                                 @Value("${app.attendance-sync.read-timeout-ms:10000}") int readTimeout) {
        this.admissionRepository = admissionRepository;
        
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * Finds student by registration number and pushes data to third-party attendance server
     */
    public AttendanceSyncResponseDTO syncStudentByRegNo(String regNo) {
        if (!attendanceSyncEnabled) {
            log.info("Attendance sync integration is disabled in configuration.");
            return AttendanceSyncResponseDTO.builder()
                    .success(false)
                    .message("Attendance sync integration is disabled in system configuration.")
                    .processedCount(0)
                    .build();
        }

        if (regNo == null || regNo.trim().isEmpty()) {
            throw new IllegalArgumentException("Registration number cannot be empty.");
        }

        Admission admission = admissionRepository.findByRegistrationNumberAndIsDeletedFalse(regNo.trim());
        if (admission == null) {
            log.warn("No active admission found for registration number: {}", regNo);
            return AttendanceSyncResponseDTO.builder()
                    .success(false)
                    .message("No active student admission record found for registration number: " + regNo)
                    .processedCount(0)
                    .build();
        }

        AttendanceStudentSyncDTO payload = mapToSyncDTO(admission);
        String endpoint = sanitizeUrl(serverUrl) + "/api/v1/students/sync-single";

        log.info("Pushing student record [{}] to attendance server at {}", regNo, endpoint);
        return executePostRequest(endpoint, payload, AttendanceSyncResponseDTO.class);
    }

    /**
     * Fetches all admissions within date range and pushes batch payload to attendance server
     */
    public AttendanceSyncResponseDTO syncStudentsByDateRange(LocalDate fromDate, LocalDate toDate) {
        if (!attendanceSyncEnabled) {
            log.info("Attendance sync integration is disabled in configuration.");
            return AttendanceSyncResponseDTO.builder()
                    .success(false)
                    .message("Attendance sync integration is disabled in system configuration.")
                    .processedCount(0)
                    .build();
        }

        if (fromDate == null || toDate == null) {
            throw new IllegalArgumentException("fromDate and toDate parameters are required.");
        }

        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("fromDate cannot be after toDate.");
        }

        List<Admission> admissions = admissionRepository.findByAdmissionDateBetweenAndIsDeletedFalse(fromDate, toDate);
        if (admissions.isEmpty()) {
            log.info("No student admissions found between {} and {}", fromDate, toDate);
            return AttendanceSyncResponseDTO.builder()
                    .success(true)
                    .message("No student admissions found between " + fromDate + " and " + toDate)
                    .processedCount(0)
                    .build();
        }

        List<AttendanceStudentSyncDTO> studentList = admissions.stream()
                .map(this::mapToSyncDTO)
                .collect(Collectors.toList());

        AttendanceBatchSyncRequestDTO batchPayload = AttendanceBatchSyncRequestDTO.builder()
                .fromDate(fromDate)
                .toDate(toDate)
                .totalRecords(studentList.size())
                .students(studentList)
                .build();

        String endpoint = sanitizeUrl(serverUrl) + "/api/v1/students/sync-batch";

        log.info("Pushing {} student records (Range: {} to {}) to attendance server at {}",
                studentList.size(), fromDate, toDate, endpoint);

        return executePostRequest(endpoint, batchPayload, AttendanceSyncResponseDTO.class);
    }

    /**
     * Map entity to DTO
     */
    public AttendanceStudentSyncDTO mapToSyncDTO(Admission admission) {
        List<String> coursesList = admission.getCourses() != null ? admission.getCourses() : Collections.emptyList();
        String primaryCourse = admission.getPackageName();
        if (primaryCourse == null || primaryCourse.trim().isEmpty()) {
            primaryCourse = !coursesList.isEmpty() ? String.join(", ", coursesList) : "";
        }

        return AttendanceStudentSyncDTO.builder()
                .regNo(admission.getRegistrationNumber())
                .fullName(admission.getFullName())
                .email(admission.getEmailPrimary())
                .mobileNo(admission.getMobilePrimary())
                .courses(coursesList)
                .courseName(primaryCourse)
                .admissionDate(admission.getAdmissionDate())
                .build();
    }

    /**
     * Execute HTTP POST call with X-API-KEY authentication header
     */
    private AttendanceSyncResponseDTO executePostRequest(String endpoint, Object body, Class<AttendanceSyncResponseDTO> responseType) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-API-KEY", apiKey);

            HttpEntity<Object> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<AttendanceSyncResponseDTO> response = restTemplate.postForEntity(endpoint, requestEntity, responseType);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Successfully pushed payload to attendance server. Response: {}", response.getBody().getMessage());
                return response.getBody();
            } else {
                log.warn("Attendance server returned non-success HTTP status: {}", response.getStatusCode());
                return AttendanceSyncResponseDTO.builder()
                        .success(false)
                        .message("Server returned HTTP status: " + response.getStatusCode())
                        .processedCount(0)
                        .build();
            }
        } catch (Exception e) {
            log.error("Failed to communicate with attendance server at {}: {}", endpoint, e.getMessage(), e);
            return AttendanceSyncResponseDTO.builder()
                    .success(false)
                    .message("Failed to communicate with attendance server: " + e.getMessage())
                    .processedCount(0)
                    .build();
        }
    }

    private String sanitizeUrl(String url) {
        if (url == null) return "";
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
