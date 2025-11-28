package com.tts.sms.service;

import com.tts.sms.dto.LeadSourceDTO;
import com.tts.sms.model.LeadSource;
import com.tts.sms.repository.LeadSourceRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LeadSourceService {

    private final LeadSourceRepository leadSourceRepository;

    @Transactional(readOnly = true)
    public Page<LeadSourceDTO> getAllLeadSources(String search, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));

        Page<LeadSource> leadSources;
        if (search != null && !search.trim().isEmpty()) {
            leadSources = leadSourceRepository.findAllActiveWithSearch(search.trim(), pageable);
        } else {
            leadSources = leadSourceRepository.findAllActive(pageable);
        }

        return leadSources.map(this::convertToDTO);
    }

    @Transactional(readOnly = true)
    public LeadSourceDTO getLeadSourceById(Long id) {
        LeadSource leadSource = leadSourceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lead source not found with id: " + id));
        return convertToDTO(leadSource);
    }

    @Transactional
    public LeadSourceDTO createLeadSource(LeadSourceDTO leadSourceDTO) {
        if (leadSourceRepository.existsBySourceTitleIgnoreCaseAndIsActiveTrue(leadSourceDTO.getSourceTitle())) {
            throw new RuntimeException("Lead source title already exists");
        }

        LeadSource leadSource = new LeadSource();
        leadSource.setSourceTitle(leadSourceDTO.getSourceTitle().trim());
        leadSource.setIsActive(true);

        LeadSource savedLeadSource = leadSourceRepository.save(leadSource);
        return convertToDTO(savedLeadSource);
    }

    @Transactional
    public LeadSourceDTO updateLeadSource(Long id, LeadSourceDTO leadSourceDTO) {
        LeadSource leadSource = leadSourceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lead source not found with id: " + id));

        if (leadSourceRepository.existsBySourceTitleIgnoreCaseAndIdNotAndIsActiveTrue(
                leadSourceDTO.getSourceTitle(), id)) {
            throw new RuntimeException("Lead source title already exists");
        }

        leadSource.setSourceTitle(leadSourceDTO.getSourceTitle().trim());
        LeadSource updatedLeadSource = leadSourceRepository.save(leadSource);
        return convertToDTO(updatedLeadSource);
    }

    @Transactional
    public void deleteLeadSource(Long id) {
        LeadSource leadSource = leadSourceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lead source not found with id: " + id));

        leadSourceRepository.delete(leadSource);
    }

    @Transactional(readOnly = true)
    public void exportToCSV(HttpServletResponse response, String search) throws IOException {
        List<LeadSource> leadSources;

        if (search != null && !search.trim().isEmpty()) {
            Pageable pageable = Pageable.unpaged();
            leadSources = leadSourceRepository.findAllActiveWithSearch(search.trim(), pageable).getContent();
        } else {
            leadSources = leadSourceRepository.findAllActive(Pageable.unpaged()).getContent();
        }

        if (leadSources.isEmpty()) {
            throw new RuntimeException("No data available to export");
        }

        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=lead_sources_export.csv");

        PrintWriter writer = response.getWriter();
        writer.println("SR. NO.,LEAD SOURCE TITLE,CREATED DATE,UPDATED DATE");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

        for (int i = 0; i < leadSources.size(); i++) {
            LeadSource leadSource = leadSources.get(i);
            writer.printf("%d,\"%s\",\"%s\",\"%s\"%n",
                    i + 1,
                    leadSource.getSourceTitle(),
                    leadSource.getCreatedDate().format(formatter),
                    leadSource.getUpdatedDate().format(formatter));
        }

        writer.flush();
    }

    private LeadSourceDTO convertToDTO(LeadSource leadSource) {
        LeadSourceDTO dto = new LeadSourceDTO();
        dto.setId(leadSource.getId());
        dto.setSourceTitle(leadSource.getSourceTitle());
        dto.setIsActive(leadSource.getIsActive());
        return dto;
    }
}