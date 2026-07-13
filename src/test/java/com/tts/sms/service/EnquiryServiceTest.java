package com.tts.sms.service;

import com.tts.sms.dto.BulkImportResponseDTO;
import com.tts.sms.dto.EnquiryRequestDTO;
import com.tts.sms.model.Enquiry;
import com.tts.sms.repository.EnquiryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

class EnquiryServiceTest {

    @Mock
    private EnquiryRepository enquiryRepository;
    @Mock
    private EnquiryMapper enquiryMapper;
    @Mock
    private CSVService csvService;

    @InjectMocks
    private EnquiryService enquiryService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void processBulkImport_counselorFormat_successAndDuplicates() {
        // Arrange
        List<EnquiryRequestDTO> dtos = new ArrayList<>();
        
        // 1. Success record
        dtos.add(EnquiryRequestDTO.builder()
                .firstName("Arun")
                .lastName("Kumar")
                .mobile("7768037215")
                .courses(List.of("PYTHON"))
                .source("GOOGLE")
                .enquiryDate(LocalDate.of(2026, 1, 1))
                .assignTo("Prachi Joshi")
                .build());

        // 2. Duplicate record (same mobile)
        dtos.add(EnquiryRequestDTO.builder()
                .firstName("Arun Duplicate")
                .lastName("Kumar")
                .mobile("7768037215")
                .courses(List.of("PYTHON"))
                .source("GOOGLE")
                .enquiryDate(LocalDate.of(2026, 1, 1))
                .assignTo("Prachi Joshi")
                .build());

        // 3. Another unique mobile
        dtos.add(EnquiryRequestDTO.builder()
                .firstName("Dhiraj")
                .lastName("Badgujar")
                .mobile("9356276745")
                .courses(List.of("JAVA"))
                .source("GOOGLE")
                .enquiryDate(LocalDate.of(2026, 1, 1))
                .assignTo("Prachi Joshi")
                .build());

        Enquiry existingEnquiry = new Enquiry();
        existingEnquiry.setMobile("7768037215");
        existingEnquiry.setFirstName("Arun");
        existingEnquiry.setLastName("Kumar");

        // Mock database calls
        when(enquiryRepository.findFirstByMobileAndIsDeletedFalse("7768037215"))
                .thenReturn(java.util.Optional.empty()) // First check: brand new record
                .thenReturn(java.util.Optional.of(existingEnquiry)); // Second check: duplicate/exists

        when(enquiryRepository.findFirstByMobileAndIsDeletedFalse("9356276745"))
                .thenReturn(java.util.Optional.empty());

        Enquiry entity1 = new Enquiry();
        Enquiry entity2 = new Enquiry();
        when(enquiryMapper.toEntity(any(EnquiryRequestDTO.class)))
                .thenReturn(entity1)
                .thenReturn(entity2);

        // Act
        BulkImportResponseDTO response = enquiryService.processBulkImport(dtos, "COUNSELOR_FORMAT");

        // Assert
        assertNotNull(response);
        assertEquals(3, response.getTotalRecords());
        assertEquals(3, response.getSuccessfulImports()); // Duplicate is updated and saved, so it's a success
        assertEquals(1, response.getDuplicateCount());
        assertEquals(0, response.getFailedImports());

        // Verify entity conversions happened twice (since 1 duplicate was updated directly and not converted from DTO)
        verify(enquiryMapper, times(2)).toEntity(any(EnquiryRequestDTO.class));
        verify(enquiryRepository, times(3)).save(any(Enquiry.class)); // 2 new saves + 1 duplicate update save
    }
}
