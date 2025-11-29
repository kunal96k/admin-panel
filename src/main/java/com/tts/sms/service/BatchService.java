package com.tts.sms.service;

import com.tts.sms.dto.*;
import com.tts.sms.exception.ResourceNotFoundException;
import com.tts.sms.exception.BadRequestException;
import com.tts.sms.model.Batch;
import com.tts.sms.model.Course;
import com.tts.sms.repository.BatchRepository;
import com.tts.sms.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class BatchService {

    private final BatchRepository batchRepository;
    private final CourseRepository courseRepository;

    /**
     * Get all batches with pagination
     */
    @Transactional(readOnly = true)
    public BatchPageResponseDTO getAllBatches(int page, int size) {
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdDate"));
            Page<Batch> batchPage = batchRepository.findByIsActiveTrueOrderByCreatedDateDesc(pageable);

            List<BatchResponseDTO> batches = batchPage.getContent().stream()
                    .map(this::convertToResponseDTO)
                    .collect(Collectors.toList());

            return new BatchPageResponseDTO(
                    batches,
                    batchPage.getTotalElements(),
                    batchPage.getTotalPages(),
                    page,
                    size
            );
        } catch (Exception e) {
            log.error("Error fetching batches: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch batches", e);
        }
    }

    /**
     * Search batches with filters
     */
    @Transactional(readOnly = true)
    public BatchPageResponseDTO searchBatches(BatchSearchDTO searchDTO) {
        try {
            Pageable pageable = PageRequest.of(
                    searchDTO.getPage(),
                    searchDTO.getSize(),
                    Sort.by(Sort.Direction.DESC, "createdDate")
            );

            Page<Batch> batchPage;

            if (searchDTO.getSearchTerm() != null || searchDTO.getStatus() != null || searchDTO.getCourseId() != null) {
                batchPage = batchRepository.searchWithFilters(
                        searchDTO.getSearchTerm(),
                        searchDTO.getStatus(),
                        searchDTO.getCourseId(),
                        pageable
                );
            } else {
                batchPage = batchRepository.findByIsActiveTrueOrderByCreatedDateDesc(pageable);
            }

            List<BatchResponseDTO> batches = batchPage.getContent().stream()
                    .map(this::convertToResponseDTO)
                    .collect(Collectors.toList());

            return new BatchPageResponseDTO(
                    batches,
                    batchPage.getTotalElements(),
                    batchPage.getTotalPages(),
                    searchDTO.getPage(),
                    searchDTO.getSize()
            );
        } catch (Exception e) {
            log.error("Error searching batches: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to search batches", e);
        }
    }

    /**
     * Get batch by ID
     */
    @Transactional(readOnly = true)
    public BatchResponseDTO getBatchById(Long id) {
        Batch batch = batchRepository.findById(id)
                .filter(Batch::getIsActive)
                .orElseThrow(() -> new ResourceNotFoundException("Batch not found with id: " + id));

        return convertToResponseDTO(batch);
    }

    /**
     * Create new batch
     */
    public BatchResponseDTO createBatch(BatchRequestDTO requestDTO, String username) {
        try {
            // Validate time range
            if (requestDTO.getStartTime().isAfter(requestDTO.getEndTime()) ||
                    requestDTO.getStartTime().equals(requestDTO.getEndTime())) {
                throw new BadRequestException("End time must be after start time");
            }

            // Validate dates if provided
            if (requestDTO.getStartDate() != null && requestDTO.getEndDate() != null) {
                if (requestDTO.getEndDate().isBefore(requestDTO.getStartDate())) {
                    throw new BadRequestException("End date must be after start date");
                }
            }

            // Generate batch number
            String batchNo = generateBatchNumber();

            // Create batch entity
            Batch batch = new Batch();
            batch.setBatchNo(batchNo);
            batch.setBatchName(requestDTO.getBatchName());
            batch.setBatchSize(requestDTO.getBatchSize());
            batch.setStartDate(requestDTO.getStartDate());
            batch.setEndDate(requestDTO.getEndDate());
            batch.setStartTime(requestDTO.getStartTime());
            batch.setEndTime(requestDTO.getEndTime());

            // Set weekly days
            batch.setIsSunday(requestDTO.getIsSunday());
            batch.setIsMonday(requestDTO.getIsMonday());
            batch.setIsTuesday(requestDTO.getIsTuesday());
            batch.setIsWednesday(requestDTO.getIsWednesday());
            batch.setIsThursday(requestDTO.getIsThursday());
            batch.setIsFriday(requestDTO.getIsFriday());
            batch.setIsSaturday(requestDTO.getIsSaturday());

            // Attach course if provided
            if (requestDTO.getCourseId() != null) {
                Course course = courseRepository.findById(requestDTO.getCourseId())
                        .orElseThrow(() -> new ResourceNotFoundException("Course not found with id: " + requestDTO.getCourseId()));
                batch.setCourse(course);
            }

            batch.setStatus(requestDTO.getStatus() != null ? requestDTO.getStatus() : "Active");
            batch.setCreatedBy(username);
            batch.setUpdatedBy(username);

            // Check for overlapping batches (optional warning)
            List<Batch> overlappingBatches = findOverlappingBatches(batch);
            if (!overlappingBatches.isEmpty()) {
                log.warn("Batch {} has overlapping schedule with {} other batch(es)",
                        batchNo, overlappingBatches.size());
            }

            Batch savedBatch = batchRepository.save(batch);
            log.info("Batch created successfully with ID: {} by user: {}", savedBatch.getId(), username);

            return convertToResponseDTO(savedBatch);
        } catch (BadRequestException | ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error creating batch: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create batch", e);
        }
    }

    /**
     * Update existing batch
     */
    public BatchResponseDTO updateBatch(Long id, BatchRequestDTO requestDTO, String username) {
        try {
            Batch batch = batchRepository.findById(id)
                    .filter(Batch::getIsActive)
                    .orElseThrow(() -> new ResourceNotFoundException("Batch not found with id: " + id));

            // Validate time range
            if (requestDTO.getStartTime().isAfter(requestDTO.getEndTime()) ||
                    requestDTO.getStartTime().equals(requestDTO.getEndTime())) {
                throw new BadRequestException("End time must be after start time");
            }

            // Validate dates if provided
            if (requestDTO.getStartDate() != null && requestDTO.getEndDate() != null) {
                if (requestDTO.getEndDate().isBefore(requestDTO.getStartDate())) {
                    throw new BadRequestException("End date must be after start date");
                }
            }

            // Update fields
            batch.setBatchName(requestDTO.getBatchName());
            batch.setBatchSize(requestDTO.getBatchSize());
            batch.setStartDate(requestDTO.getStartDate());
            batch.setEndDate(requestDTO.getEndDate());
            batch.setStartTime(requestDTO.getStartTime());
            batch.setEndTime(requestDTO.getEndTime());

            // Update weekly days
            batch.setIsSunday(requestDTO.getIsSunday());
            batch.setIsMonday(requestDTO.getIsMonday());
            batch.setIsTuesday(requestDTO.getIsTuesday());
            batch.setIsWednesday(requestDTO.getIsWednesday());
            batch.setIsThursday(requestDTO.getIsThursday());
            batch.setIsFriday(requestDTO.getIsFriday());
            batch.setIsSaturday(requestDTO.getIsSaturday());

            // Update course if provided
            if (requestDTO.getCourseId() != null) {
                Course course = courseRepository.findById(requestDTO.getCourseId())
                        .orElseThrow(() -> new ResourceNotFoundException("Course not found with id: " + requestDTO.getCourseId()));
                batch.setCourse(course);
            } else {
                batch.setCourse(null);
            }

            if (requestDTO.getStatus() != null) {
                batch.setStatus(requestDTO.getStatus());
            }

            batch.setUpdatedBy(username);

            Batch updatedBatch = batchRepository.save(batch);
            log.info("Batch updated successfully with ID: {} by user: {}", id, username);

            return convertToResponseDTO(updatedBatch);
        } catch (BadRequestException | ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error updating batch: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to update batch", e);
        }
    }

    /**
     * Hard delete batch
     */
    public void deleteBatch(Long id, String username) {
        try {
            Batch batch = batchRepository.findById(id)
                    .filter(Batch::getIsActive)
                    .orElseThrow(() -> new ResourceNotFoundException("Batch not found with id: " + id));

            // Hard delete
            batchRepository.delete(batch);
            log.info("Batch deleted successfully with ID: {} by user: {}", id, username);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error deleting batch: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to delete batch", e);
        }
    }

    /**
     * Attach course to batch
     */
    public BatchResponseDTO attachCourse(Long batchId, Long courseId, String username) {
        try {
            Batch batch = batchRepository.findById(batchId)
                    .filter(Batch::getIsActive)
                    .orElseThrow(() -> new ResourceNotFoundException("Batch not found with id: " + batchId));

            Course course = courseRepository.findById(courseId)
                    .orElseThrow(() -> new ResourceNotFoundException("Course not found with id: " + courseId));

            batch.setCourse(course);
            batch.setUpdatedBy(username);

            Batch updatedBatch = batchRepository.save(batch);
            log.info("Course {} attached to batch {} by user: {}", courseId, batchId, username);

            return convertToResponseDTO(updatedBatch);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error attaching course to batch: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to attach course", e);
        }
    }

    /**
     * Generate unique batch number
     */
    private String generateBatchNumber() {
        try {
            // Try to get max batch number using native query
            Integer maxNumber = batchRepository.findMaxBatchNumber();
            int nextNumber = (maxNumber != null ? maxNumber : 0) + 1;

            // Ensure uniqueness
            String batchNo = String.valueOf(nextNumber);
            int attempts = 0;
            while (batchRepository.existsByBatchNo(batchNo) && attempts < 10) {
                nextNumber++;
                batchNo = String.valueOf(nextNumber);
                attempts++;
            }

            return batchNo;
        } catch (Exception e) {
            log.warn("Native query failed, using fallback method: {}", e.getMessage());

            // Fallback: Get all batch numbers and find max manually
            try {
                List<String> allBatchNumbers = batchRepository.findAllBatchNumbers();
                int maxNumber = allBatchNumbers.stream()
                        .filter(bn -> bn.matches("\\d+")) // Only numeric batch numbers
                        .mapToInt(Integer::parseInt)
                        .max()
                        .orElse(0);

                int nextNumber = maxNumber + 1;
                String batchNo = String.valueOf(nextNumber);

                // Ensure uniqueness
                int attempts = 0;
                while (batchRepository.existsByBatchNo(batchNo) && attempts < 10) {
                    nextNumber++;
                    batchNo = String.valueOf(nextNumber);
                    attempts++;
                }

                return batchNo;
            } catch (Exception fallbackException) {
                log.error("Fallback batch number generation failed: {}", fallbackException.getMessage());
                // Ultimate fallback: timestamp-based with prefix
                return "B" + System.currentTimeMillis() % 100000;
            }
        }
    }

    /**
     * Find overlapping batches
     */
    private List<Batch> findOverlappingBatches(Batch batch) {
        Long batchId = batch.getId() != null ? batch.getId() : 0L;
        return batchRepository.findOverlappingBatches(
                batchId,
                batch.getStartTime(),
                batch.getEndTime(),
                batch.getIsSunday(),
                batch.getIsMonday(),
                batch.getIsTuesday(),
                batch.getIsWednesday(),
                batch.getIsThursday(),
                batch.getIsFriday(),
                batch.getIsSaturday()
        );
    }

    /**
     * Convert entity to response DTO
     */
    private BatchResponseDTO convertToResponseDTO(Batch batch) {
        BatchResponseDTO dto = new BatchResponseDTO();
        dto.setId(batch.getId());
        dto.setBatchNo(batch.getBatchNo());
        dto.setBatchName(batch.getBatchName());
        dto.setBatchSize(batch.getBatchSize());
        dto.setStartDate(batch.getStartDate());
        dto.setEndDate(batch.getEndDate());
        dto.setStartTime(batch.getStartTime());
        dto.setEndTime(batch.getEndTime());
        dto.setIsSunday(batch.getIsSunday());
        dto.setIsMonday(batch.getIsMonday());
        dto.setIsTuesday(batch.getIsTuesday());
        dto.setIsWednesday(batch.getIsWednesday());
        dto.setIsThursday(batch.getIsThursday());
        dto.setIsFriday(batch.getIsFriday());
        dto.setIsSaturday(batch.getIsSaturday());

        if (batch.getCourse() != null) {
            dto.setCourseName(batch.getCourse().getCourseName());
            dto.setCourseId(batch.getCourse().getId());
        }

        dto.setStatus(batch.getStatus());
        dto.setIsActive(batch.getIsActive());
        dto.setCreatedBy(batch.getCreatedBy());
        dto.setUpdatedBy(batch.getUpdatedBy());

        return dto;
    }
}