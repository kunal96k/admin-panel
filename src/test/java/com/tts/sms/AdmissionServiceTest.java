package com.tts.sms;

import com.tts.sms.dto.*;
import com.tts.sms.exception.ResourceNotFoundException;
import com.tts.sms.model.Admission;
import com.tts.sms.model.Enquiry;
import com.tts.sms.model.FeeInstallment;
import com.tts.sms.model.Fees;
import com.tts.sms.repository.*;
import com.tts.sms.service.AdmissionMapper;
import com.tts.sms.service.AdmissionService;
import com.tts.sms.service.CSVService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class) // 1. Initializes Mockito
class AdmissionServiceTest {

    // 2. Mock all dependencies found in AdmissionService
    @Mock private AdmissionRepository admissionRepository;
    @Mock private EnquiryRepository enquiryRepository;
    @Mock private FeeInstallmentRepository feeInstallmentRepository;
    @Mock private FeesRepository feesRepository;
    @Mock private AdmissionMapper admissionMapper;
    @Mock private CSVService csvService;

    // 3. Inject mocks into the service
    @InjectMocks
    private AdmissionService admissionService;

    private Admission admission;
    private AdmissionRequestDTO requestDTO;
    private AdmissionResponseDTO responseDTO;

    @BeforeEach
    void setUp() {
        // Prepare dummy data before each test
        admission = new Admission();
        admission.setId(1L);
        admission.setFirstName("kunal");
        admission.setLastName("patil");
        admission.setRegistrationNumber("REG8000");
        admission.setMobilePrimary("9876543210");
        admission.setIsDeleted(false);
        admission.setTotalReceivableFees(50000.0);

        requestDTO = new AdmissionRequestDTO();
        requestDTO.setFirstName("kunal");
        requestDTO.setLastName("patil");
        requestDTO.setMobilePrimary("9876543210");
        requestDTO.setTotalReceivableFees(50000.0);

        responseDTO = new AdmissionResponseDTO();
        responseDTO.setRegistrationNumber("REG8000");
    }

    // --- TEST 1: Get All Admissions (Pagination) ---
    @Test
    @DisplayName("Should return paginated admissions")
    void testGetAllAdmissions() {
        // Arrange
        Page<Admission> page = new PageImpl<>(List.of(admission));
        when(admissionRepository.findByIsDeletedFalse(any(Pageable.class))).thenReturn(page);
        when(admissionMapper.toResponseDTO(any(Admission.class))).thenReturn(responseDTO);

        // Act
        Page<AdmissionResponseDTO> result = admissionService.getAllAdmissions(0, 10);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(admissionRepository, times(1)).findByIsDeletedFalse(any(Pageable.class));
    }

    // --- TEST 2: Create Admission (Happy Path - New Student) ---
    @Test
    @DisplayName("Should create new admission and generate fees")
    void testCreateAdmission_NewStudent() {
        // Arrange
        // 1. Mock Enquiry Check
        when(enquiryRepository.findByMobileAndIsDeletedFalse(anyString())).thenReturn(Optional.empty());

        // 2. Mock Mapping
        when(admissionMapper.toEntity(any(AdmissionRequestDTO.class))).thenReturn(admission);

        // 3. Mock Counter Initialization (Important logic in your service)
        when(admissionRepository.findMaxRegistrationNumber(anyString())).thenReturn("REG7999");

        // 4. Mock Saving Admission
        when(admissionRepository.save(any(Admission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // 5. Mock Fees Check (Should not exist yet)
        when(feesRepository.findByRegistrationNumberAndIsDeletedFalse(anyString())).thenReturn(Optional.empty());

        // 6. Mock Mapping Response
        when(admissionMapper.toResponseDTO(any(Admission.class))).thenReturn(responseDTO);

        // Act
        AdmissionResponseDTO result = admissionService.createAdmission(requestDTO);

        // Assert
        assertNotNull(result);
        verify(admissionRepository, times(1)).save(any(Admission.class));
        // Verify Fees were created because it's a new "REG" number
        verify(feesRepository, times(1)).save(any(Fees.class));
    }

    // --- TEST 3: Create Admission (Old CSV Import logic) ---
    @Test
    @DisplayName("Should create admission but SKIP fees for old CSV format")
    void testCreateAdmission_OldCsvImport() {
        // Arrange
        requestDTO.setRegistrationNumber("OLD1234"); // patils not start with REG
        admission.setRegistrationNumber("OLD1234");

        when(enquiryRepository.findByMobileAndIsDeletedFalse(anyString())).thenReturn(Optional.empty());
        when(admissionMapper.toEntity(any(AdmissionRequestDTO.class))).thenReturn(admission);
        when(admissionRepository.save(any(Admission.class))).thenReturn(admission);
        when(admissionMapper.toResponseDTO(any(Admission.class))).thenReturn(responseDTO);

        // Act
        admissionService.createAdmission(requestDTO);

        // Assert
        verify(admissionRepository, times(1)).save(any(Admission.class));
        // Verify feesRepository.save was NEVER called because RegNo != REG...
        verify(feesRepository, never()).save(any(Fees.class));
    }

    // --- TEST 4: Get By ID (Success) ---
    @Test
    @DisplayName("Should return admission by ID")
    void testGetAdmissionById_Found() {
        when(admissionRepository.findById(1L)).thenReturn(Optional.of(admission));
        when(admissionMapper.toResponseDTO(admission)).thenReturn(responseDTO);

        AdmissionResponseDTO result = admissionService.getAdmissionById(1L);

        assertEquals("REG8000", result.getRegistrationNumber());
    }

    // --- TEST 5: Get By ID (Not Found Exception) ---
    @Test
    @DisplayName("Should throw exception when admission not found")
    void testGetAdmissionById_NotFound() {
        when(admissionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> {
            admissionService.getAdmissionById(99L);
        });
    }

    // --- TEST 6: Soft Delete Admission ---
    @Test
    @DisplayName("Should soft delete admission and linked fees")
    void testDeleteAdmission() {
        // Arrange
        when(admissionRepository.findById(1L)).thenReturn(Optional.of(admission));
        Fees mockFees = new Fees();
        when(feesRepository.findByRegistrationNumberAndIsDeletedFalse("REG8000"))
                .thenReturn(Optional.of(mockFees));

        // Act
        admissionService.deleteAdmission(1L);

        // Assert
        assertTrue(admission.getIsDeleted()); // Check entity state change
        verify(admissionRepository).save(admission); // Verify save called

        assertTrue(mockFees.getIsDeleted()); // Check fees state change
        verify(feesRepository).save(mockFees); // Verify fees save called
    }

    // --- TEST 7: Generate Installments ---
    @Test
    @DisplayName("Should generate installments correctly")
    void testGenerateInstallments() {
        // Arrange
        InstallmentConfigDTO config = new InstallmentConfigDTO();
        config.setNumberOfInstallments(2);
        config.setStartDate(LocalDate.now());
        config.setDaysBetween(30);

        List<FeeInstallment> mockInstallments = new ArrayList<>();
        when(feeInstallmentRepository.saveAll(anyList())).thenReturn(mockInstallments);

        // Mock finding fees to update config
        when(feesRepository.findByRegistrationNumberAndIsDeletedFalse(anyString()))
                .thenReturn(Optional.of(new Fees()));

        // Act
        admissionService.generateInstallments("REG8000", config, 1000.0);

        // Assert
        verify(feeInstallmentRepository).deleteByRegistrationNumber("REG8000"); // Should clean up old
        verify(feeInstallmentRepository).saveAll(anyList()); // Should save new
    }

    // --- TEST 8: Atomic Counter Initialization ---
    @Test
    @DisplayName("Should handle missing max registration number gracefully")
    void testCounterInitialization_FirstRun() {
        // This tests the generateRegistrationNumber logic indirectly via createAdmission
        when(enquiryRepository.findByMobileAndIsDeletedFalse(anyString())).thenReturn(Optional.empty());
        when(admissionMapper.toEntity(any())).thenReturn(admission);
        when(admissionRepository.save(any())).thenReturn(admission);
        when(admissionMapper.toResponseDTO(any())).thenReturn(responseDTO);

        // Mock repository returning null (empty DB)
        when(admissionRepository.findMaxRegistrationNumber("REG")).thenReturn(null);

        admissionService.createAdmission(requestDTO);

        // Logic in service says if null, start at 8000.
        // We can't easily assert the AtomicInteger private static field,
        // but we verify the code runs without error.
        verify(admissionRepository).findMaxRegistrationNumber("REG");
    }

    // --- TEST 9: Update Admission ---
    @Test
    @DisplayName("Should update admission details")
    void testUpdateAdmission() {
        when(admissionRepository.findById(1L)).thenReturn(Optional.of(admission));
        when(admissionRepository.save(any(Admission.class))).thenReturn(admission);
        when(admissionMapper.toResponseDTO(any())).thenReturn(responseDTO);

        // The mock mapper needs to do nothing on void method updateEntityFromDTO
        doNothing().when(admissionMapper).updateEntityFromDTO(any(), any());

        AdmissionResponseDTO result = admissionService.updateAdmission(1L, requestDTO);

        assertNotNull(result);
        assertEquals("SYSTEM", admission.getUpdatedBy());
        verify(admissionRepository).save(admission);
    }
}