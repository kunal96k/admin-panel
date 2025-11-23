package com.tts.sms.service;

import com.tts.sms.dto.FollowUpDTO;
import com.tts.sms.model.Enquiry;
import com.tts.sms.model.FollowUp;
import com.tts.sms.repository.EnquiryRepository;
import com.tts.sms.repository.FollowUpRepository;
import com.tts.sms.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FollowUpService {

    private final FollowUpRepository followUpRepository;
    private final EnquiryRepository enquiryRepository;

    /**
     * Get follow-up history for an enquiry
     */
    @Transactional(readOnly = true)
    public List<FollowUpDTO> getFollowUpHistory(Long enquiryId) {
        log.debug("Fetching follow-up history for enquiry: {}", enquiryId);

        // Verify enquiry exists
        enquiryRepository.findById(enquiryId)
                .orElseThrow(() -> new ResourceNotFoundException("Enquiry not found with id: " + enquiryId));

        List<FollowUp> followUps = followUpRepository.findByEnquiryIdOrderByFollowUpDateDesc(enquiryId);

        return followUps.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Add a new follow-up
     */
    @Transactional
    public FollowUpDTO addFollowUp(Long enquiryId, FollowUpDTO dto) {
        log.debug("Adding follow-up for enquiry: {}", enquiryId);

        Enquiry enquiry = enquiryRepository.findById(enquiryId)
                .orElseThrow(() -> new ResourceNotFoundException("Enquiry not found with id: " + enquiryId));

        FollowUp followUp = FollowUp.builder()
                .enquiry(enquiry)
                .followUpDate(dto.getFollowUpDate())
                .nextFollowUpDate(dto.getNextFollowUpDate())
                .mode(dto.getMode())
                .note(dto.getNote())
                .createdAt(LocalDateTime.now())
                .build();

        // Update enquiry follow-up date and status
        enquiry.setFollowupDate(dto.getNextFollowUpDate());
        if (dto.getNextFollowUpDate() != null) {
            enquiry.setStatus("Follow-up Scheduled");
        }
        enquiryRepository.save(enquiry);

        FollowUp saved = followUpRepository.save(followUp);
        log.info("Follow-up created with id: {} for enquiry: {}", saved.getId(), enquiryId);

        return toDTO(saved);
    }

    /**
     * Delete a follow-up
     */
    @Transactional
    public void deleteFollowUp(Long followUpId) {
        log.debug("Deleting follow-up with id: {}", followUpId);

        FollowUp followUp = followUpRepository.findById(followUpId)
                .orElseThrow(() -> new ResourceNotFoundException("Follow-up not found with id: " + followUpId));

        followUpRepository.delete(followUp);
        log.info("Deleted follow-up with id: {}", followUpId);
    }

    /**
     * Convert entity to DTO
     */
    private FollowUpDTO toDTO(FollowUp followUp) {
        if (followUp == null) return null;

        return FollowUpDTO.builder()
                .id(followUp.getId())
                .enquiryId(followUp.getEnquiry().getId())
                .followUpDate(followUp.getFollowUpDate())
                .nextFollowUpDate(followUp.getNextFollowUpDate())
                .mode(followUp.getMode())
                .note(followUp.getNote())
                .createdAt(followUp.getCreatedAt())
                .build();
    }
}