package com.tts.sms.service;

import com.tts.sms.dto.AdmissionResponseDTO;
import com.tts.sms.exception.ResourceNotFoundException;
import com.tts.sms.model.Admission;
import com.tts.sms.repository.AdmissionRepository;
import com.tts.sms.repository.EnquiryRepository;
import com.tts.sms.repository.FeeInstallmentRepository;
import com.tts.sms.repository.FeesRepository;
import com.tts.sms.repository.FeeReceiptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.lang.reflect.Method;

import static org.hibernate.validator.internal.util.Contracts.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class AdmissionServiceTest {

    @Mock
    private AdmissionRepository admissionRepository;
    @Mock
    private EnquiryRepository enquiryRepository;
    @Mock
    private FeeInstallmentRepository feeInstallmentRepository;
    @Mock
    private FeesRepository feesRepository;
    @Mock
    private FeeReceiptRepository feeReceiptRepository;
    @Mock
    private AdmissionMapper admissionMapper;
    @Mock
    private CSVService csvService;

    @InjectMocks
    private AdmissionService admissionService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void getByRegistrationNumber_success() {
        // Arrange
        String regNo = "REG8001";

        Admission admission = new Admission();
        admission.setRegistrationNumber(regNo);

        AdmissionResponseDTO responseDTO = new AdmissionResponseDTO();

        when(admissionRepository.findByRegistrationNumberAndIsDeletedFalse(regNo))
                .thenReturn(admission);

        when(admissionMapper.toResponseDTO(admission))
                .thenReturn(responseDTO);

        // Act
        AdmissionResponseDTO result =
                admissionService.getByRegistrationNumber(regNo);

        // Assert
        assertNotNull(result);
        verify(admissionRepository).findByRegistrationNumberAndIsDeletedFalse(regNo);
        verify(admissionMapper).toResponseDTO(admission);
    }

    @Test
    void getByRegistrationNumber_notFound() {
        // Arrange
        String regNo = "REG9999";

        when(admissionRepository.findByRegistrationNumberAndIsDeletedFalse(regNo))
                .thenReturn(null);

        // Act + Assert
        assertThrows(ResourceNotFoundException.class,
                () -> admissionService.getByRegistrationNumber(regNo));

        verify(admissionRepository).findByRegistrationNumberAndIsDeletedFalse(regNo);
        verifyNoInteractions(admissionMapper);
    }

    private String invokeConvertToSnakeCase(String input) throws Exception {
        Method method = AdmissionService.class
                .getDeclaredMethod("convertToSnakeCase", String.class);

        method.setAccessible(true);

        return (String) method.invoke(admissionService, input);
    }

    @Test
    void convertToSnakeCase_knownFields() throws Exception {
        // Act & Assert
        assertEquals("created_at", invokeConvertToSnakeCase("createdAt"));
        assertEquals("updated_at", invokeConvertToSnakeCase("updatedAt"));
        assertEquals("admission_date", invokeConvertToSnakeCase("admissionDate"));
        assertEquals("registration_number", invokeConvertToSnakeCase("registrationNumber"));
        assertEquals("mobile_primary", invokeConvertToSnakeCase("mobilePrimary"));
    }

    @Test
    void convertToSnakeCase_genericCamelCase() throws Exception {
        // Act & Assert
        assertEquals("first_name", invokeConvertToSnakeCase("firstName"));
        assertEquals("last_name", invokeConvertToSnakeCase("lastName"));
        assertEquals("total_fees", invokeConvertToSnakeCase("totalFees"));
    }
    @Test
    void convertToSnakeCase_nullInput() throws Exception {
        // Act
        String result = invokeConvertToSnakeCase(null);

        // Assert
        assertEquals("created_at", result);
    }

}
