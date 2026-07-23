package com.tts.sms.service;

import com.tts.sms.dto.AttendanceStudentSyncDTO;
import com.tts.sms.model.Admission;
import com.tts.sms.repository.AdmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceSyncServiceTest {

    @Mock
    private AdmissionRepository admissionRepository;

    private AttendanceSyncService attendanceSyncService;

    @BeforeEach
    void setUp() {
        attendanceSyncService = new AttendanceSyncService(admissionRepository, 1000, 1000);
        org.springframework.test.util.ReflectionTestUtils.setField(attendanceSyncService, "attendanceSyncEnabled", true);
        org.springframework.test.util.ReflectionTestUtils.setField(attendanceSyncService, "serverUrl", "https://attendance.ttsnasik.com");
        org.springframework.test.util.ReflectionTestUtils.setField(attendanceSyncService, "apiKey", "NbLrdNBkxdCx5liYBx4VwnvmDoY1Q-dx2HiyiaRWzAsq4qZ9ij0g9sMGS0bNMFqTeEY-aaEvh7bl9vCQ-EP-Pg");
    }

    @Test
    void testMapToSyncDTO_Success() {
        Admission admission = Admission.builder()
                .registrationNumber("TTS2026001")
                .firstName("Kunal")
                .middleName("R.")
                .lastName("Patil")
                .emailPrimary("kunal@example.com")
                .mobilePrimary("9876543210")
                .packageName("Java Fullstack Developer")
                .courses(List.of("Java", "Spring Boot", "React"))
                .admissionDate(LocalDate.of(2026, 7, 15))
                .build();

        AttendanceStudentSyncDTO dto = attendanceSyncService.mapToSyncDTO(admission);

        assertNotNull(dto);
        assertEquals("TTS2026001", dto.getRegNo());
        assertEquals("Kunal R. Patil", dto.getFullName());
        assertEquals("kunal@example.com", dto.getEmail());
        assertEquals("9876543210", dto.getMobileNo());
        assertEquals("Java Fullstack Developer", dto.getCourseName());
        assertEquals(3, dto.getCourses().size());
        assertEquals(LocalDate.of(2026, 7, 15), dto.getAdmissionDate());
    }

    @Test
    void testSyncStudentByRegNo_NotFound() {
        when(admissionRepository.findByRegistrationNumberAndIsDeletedFalse("NON_EXISTENT")).thenReturn(null);

        var response = attendanceSyncService.syncStudentByRegNo("NON_EXISTENT");

        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertTrue(response.getMessage().contains("No active student admission record found"));
        assertEquals(0, response.getProcessedCount());
        verify(admissionRepository, times(1)).findByRegistrationNumberAndIsDeletedFalse("NON_EXISTENT");
    }

    @Test
    void testSyncStudentsByDateRange_EmptyResults() {
        LocalDate fromDate = LocalDate.of(2026, 1, 1);
        LocalDate toDate = LocalDate.of(2026, 1, 31);

        when(admissionRepository.findByAdmissionDateBetweenAndIsDeletedFalse(fromDate, toDate))
                .thenReturn(Collections.emptyList());

        var response = attendanceSyncService.syncStudentsByDateRange(fromDate, toDate);

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertTrue(response.getMessage().contains("No student admissions found"));
        assertEquals(0, response.getProcessedCount());
    }

    @Test
    void testSyncStudentsByDateRange_InvalidRange() {
        LocalDate fromDate = LocalDate.of(2026, 5, 1);
        LocalDate toDate = LocalDate.of(2026, 1, 1);

        assertThrows(IllegalArgumentException.class, () ->
                attendanceSyncService.syncStudentsByDateRange(fromDate, toDate)
        );
    }
}
