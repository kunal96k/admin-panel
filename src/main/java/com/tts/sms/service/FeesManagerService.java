package com.tts.sms.service;

import com.tts.sms.dto.*;
import com.tts.sms.exception.ResourceNotFoundException;
import com.tts.sms.model.*;
import com.tts.sms.repository.*;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeesManagerService {

    private final AdmissionRepository admissionRepository;
    private final FeesRepository feesRepository;
    private final FeeReceiptRepository feeReceiptRepository;
    private final FeeRefundRepository feeRefundRepository;
    private final FeeInstallmentRepository feeInstallmentRepository;
    private final CSVService csvService;

    private static final DateTimeFormatter[] DATE_FORMATTERS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy")
    };

    // ==================== FEES SUMMARY (FROM FEES TABLE) ====================

    @Transactional(readOnly = true)
    public Page<FeesSummaryDTO> getAllFeesSummary(int page, int size) {
        log.debug("Fetching fees summary - page: {}, size: {}", page, size);

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Fees> fees = feesRepository.findByIsDeletedFalse(pageable);

        return fees.map(this::toFeesSummaryDTO);
    }

    private FeesSummaryDTO toFeesSummaryDTO(Fees fees) {
        return FeesSummaryDTO.builder()
                .admissionId(fees.getAdmissionId())
                .registrationNumber(fees.getRegistrationNumber())
                .studentName(fees.getStudentName())
                .mobile(fees.getMobile())
                .course(fees.getCourse() != null ? fees.getCourse() : "N/A")
                .totalFees(fees.getTotalFees())
                .totalPaid(fees.getTotalPaid())
                .feesDue(fees.getFeesDue())
                .feesRefund(fees.getFeesRefund())
                .dueDate(fees.getDueDate())
                .status(fees.getStatus())
                .totalInstallments(0)
                .paidInstallments(0)
                .pendingInstallments(0)
                .build();
    }

    // ==================== CSV IMPORT (FIXED) ====================

    @Transactional
    public FeesBulkImportResponseDTO bulkImportFeesCSV(MultipartFile file) {
        log.info("🔄 Starting bulk fees CSV import: {}", file.getOriginalFilename());

        try {
            List<FeesCSVImportDTO> dtos = csvService.parseFeesCSV(file);
            return processBulkFeesImport(dtos);

        } catch (Exception e) {
            log.error("❌ Error during bulk fees import", e);
            return FeesBulkImportResponseDTO.builder()
                    .success(false)
                    .totalRecords(0)
                    .successfulImports(0)
                    .failedImports(0)
                    .message("Failed to process CSV: " + e.getMessage())
                    .build();
        }
    }

    @Transactional
    public FeesBulkImportResponseDTO processBulkFeesImport(List<FeesCSVImportDTO> dtos) {
        log.info("🔄 Processing {} fees records", dtos.size());

        int successCount = 0;
        int updateCount = 0;
        int createCount = 0;
        List<FeesBulkImportResponseDTO.ImportError> errors = new ArrayList<>();

        for (int i = 0; i < dtos.size(); i++) {
            final int rowNumber = i + 2;
            FeesCSVImportDTO dto = dtos.get(i);

            try {
                // Validate required fields
                if (dto.getRegistrationNumber() == null ||
                        dto.getRegistrationNumber().equals("N/A") ||
                        dto.getRegistrationNumber().isEmpty()) {

                    errors.add(FeesBulkImportResponseDTO.ImportError.builder()
                            .rowNumber(rowNumber)
                            .fieldName("registrationNumber")
                            .errorMessage("Registration number is required")
                            .rejectedValue(dto.getRegistrationNumber())
                            .build());
                    continue;
                }

                // Check if fees record exists
                Optional<Fees> existingFees = feesRepository
                        .findByRegistrationNumberAndIsDeletedFalse(dto.getRegistrationNumber());

                Fees fees;
                if (existingFees.isPresent()) {
                    // UPDATE existing record
                    fees = existingFees.get();
                    fees.setStudentName(dto.getStudentName());
                    fees.setMobile(dto.getMobile());
                    fees.setTotalFees(dto.getTotalFees());
                    fees.setFeesDue(dto.getFeesDue());
                    fees.setTotalPaid(dto.getTotalPaid());
                    fees.setDueDate(dto.getDueDate());
                    fees.setFeesRefund(dto.getFeesRefund());
                    fees.setStatus(dto.getStatus());
                    fees.setCourse(dto.getCourse());
                    fees.setUpdatedBy("CSV_IMPORT");
                    updateCount++;

                    log.debug("✏️ Updating fees for: {}", dto.getRegistrationNumber());
                } else {
                    // CREATE new record
                    fees = Fees.builder()
                            .registrationNumber(dto.getRegistrationNumber())
                            .studentName(dto.getStudentName())
                            .mobile(dto.getMobile())
                            .totalFees(dto.getTotalFees())
                            .feesDue(dto.getFeesDue())
                            .totalPaid(dto.getTotalPaid())
                            .dueDate(dto.getDueDate())
                            .feesRefund(dto.getFeesRefund())
                            .status(dto.getStatus())
                            .course(dto.getCourse())
                            .createdBy("CSV_IMPORT")
                            .build();
                    createCount++;

                    log.debug("➕ Creating new fees for: {}", dto.getRegistrationNumber());
                }

                // Try to link with admission if exists
                Admission admission = admissionRepository
                        .findByRegistrationNumberAndIsDeletedFalse(dto.getRegistrationNumber());
                if (admission != null) {
                    fees.setAdmissionId(admission.getId());
                }

                feesRepository.save(fees);
                successCount++;

                log.info("✅ Row {}: {} fees for {}",
                        rowNumber,
                        existingFees.isPresent() ? "Updated" : "Created",
                        dto.getRegistrationNumber());

            } catch (Exception e) {
                log.error("❌ Row {}: Error - {}", rowNumber, e.getMessage(), e);

                errors.add(FeesBulkImportResponseDTO.ImportError.builder()
                        .rowNumber(rowNumber)
                        .fieldName("processing")
                        .errorMessage(e.getMessage())
                        .rejectedValue(dto.getRegistrationNumber())
                        .build());
            }
        }

        int failedCount = dtos.size() - successCount;

        log.info("📊 IMPORT RESULT: {}/{} processed (Created: {}, Updated: {}), {} failed",
                successCount, dtos.size(), createCount, updateCount, failedCount);

        return FeesBulkImportResponseDTO.builder()
                .success(successCount > 0)
                .totalRecords(dtos.size())
                .successfulImports(successCount)
                .failedImports(failedCount)
                .errors(errors)
                .message(String.format("%d/%d records processed (Created: %d, Updated: %d)",
                        successCount, dtos.size(), createCount, updateCount))
                .build();
    }

    // ==================== FEE RECEIPT ====================

    @Transactional
    public FeeReceiptResponseDTO createFeeReceipt(FeeReceiptRequestDTO requestDTO) {
        log.debug("Creating fee receipt for admission: {}", requestDTO.getAdmissionId());

        Admission admission = admissionRepository.findById(requestDTO.getAdmissionId())
                .filter(a -> !a.getIsDeleted())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Admission not found: " + requestDTO.getAdmissionId()));

        FeeReceipt receipt = FeeReceipt.builder()
                .receiptNumber(generateReceiptNumber())
                .invoiceNumber(generateInvoiceNumber())
                .admissionId(requestDTO.getAdmissionId())
                .installmentId(requestDTO.getInstallmentId())
                .receiptDate(requestDTO.getReceiptDate() != null ? requestDTO.getReceiptDate() : LocalDate.now())
                .amountReceived(requestDTO.getAmountReceived())
                .previousPaid(requestDTO.getPreviousPaid())
                .totalFees(requestDTO.getTotalFees())
                .pendingFees(requestDTO.getPendingFees())
                .gstEnabled(requestDTO.getGstEnabled() != null ? requestDTO.getGstEnabled() : false)
                .sgstPercent(requestDTO.getSgstPercent())
                .cgstPercent(requestDTO.getCgstPercent())
                .invoiceValue(requestDTO.getInvoiceValue())
                .paymentMode(requestDTO.getPaymentMode())
                .bankName(requestDTO.getBankName())
                .chequeNumber(requestDTO.getChequeNumber())
                .chequeDate(requestDTO.getChequeDate())
                .transactionNumber(requestDTO.getTransactionNumber())
                .ifscCode(requestDTO.getIfscCode())
                .onlinePaymentMode(requestDTO.getOnlinePaymentMode())
                .nextDueDate(requestDTO.getNextDueDate())
                .receiptType(requestDTO.getReceiptType() != null ? requestDTO.getReceiptType() : "Regular")
                .notes(requestDTO.getNotes())
                .createdBy("SYSTEM")
                .build();

        FeeReceipt saved = feeReceiptRepository.save(receipt);

        if (requestDTO.getInstallmentId() != null) {
            updateInstallmentStatus(requestDTO.getInstallmentId(), "Paid");
        }

        log.info("Created fee receipt: {}", saved.getReceiptNumber());
        return toReceiptResponseDTO(saved, admission);
    }

    @Transactional(readOnly = true)
    public List<FeeReceiptResponseDTO> getReceiptsByAdmission(Long admissionId) {
        log.debug("Fetching receipts for admission: {}", admissionId);

        Admission admission = admissionRepository.findById(admissionId)
                .filter(a -> !a.getIsDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Admission not found: " + admissionId));

        return feeReceiptRepository
                .findByAdmissionIdAndIsDeletedFalseOrderByReceiptDateDesc(admissionId)
                .stream()
                .map(r -> toReceiptResponseDTO(r, admission))
                .toList();
    }

    @Transactional
    public void deleteFeeReceipt(Long receiptId) {
        log.debug("Deleting fee receipt: {}", receiptId);

        FeeReceipt receipt = feeReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt not found: " + receiptId));

        receipt.setIsDeleted(true);
        receipt.setDeletedAt(java.time.LocalDateTime.now());
        feeReceiptRepository.save(receipt);

        log.info("Deleted fee receipt: {}", receipt.getReceiptNumber());
    }

    // ==================== FEE REFUND ====================

    @Transactional
    public FeeRefundResponseDTO createFeeRefund(FeeRefundRequestDTO requestDTO) {
        log.debug("Creating fee refund for admission: {}", requestDTO.getAdmissionId());

        Admission admission = admissionRepository.findById(requestDTO.getAdmissionId())
                .filter(a -> !a.getIsDeleted())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Admission not found: " + requestDTO.getAdmissionId()));

        FeeRefund refund = FeeRefund.builder()
                .refundNumber(generateRefundNumber())
                .admissionId(requestDTO.getAdmissionId())
                .refundDate(requestDTO.getRefundDate() != null ? requestDTO.getRefundDate() : LocalDate.now())
                .refundAmount(requestDTO.getRefundAmount())
                .totalFees(requestDTO.getTotalFees())
                .paidFees(requestDTO.getPaidFees())
                .pendingFees(requestDTO.getPendingFees())
                .paymentMode(requestDTO.getPaymentMode())
                .bankName(requestDTO.getBankName())
                .chequeNumber(requestDTO.getChequeNumber())
                .chequeDate(requestDTO.getChequeDate())
                .transactionNumber(requestDTO.getTransactionNumber())
                .ifscCode(requestDTO.getIfscCode())
                .onlinePaymentMode(requestDTO.getOnlinePaymentMode())
                .paymentClear(requestDTO.getPaymentClear() != null ? requestDTO.getPaymentClear() : false)
                .notes(requestDTO.getNotes())
                .createdBy("SYSTEM")
                .build();

        FeeRefund saved = feeRefundRepository.save(refund);

        log.info("Created fee refund: {}", saved.getRefundNumber());
        return toRefundResponseDTO(saved, admission);
    }

    @Transactional(readOnly = true)
    public List<FeeRefundResponseDTO> getRefundsByAdmission(Long admissionId) {
        log.debug("Fetching refunds for admission: {}", admissionId);

        Admission admission = admissionRepository.findById(admissionId)
                .filter(a -> !a.getIsDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Admission not found: " + admissionId));

        return feeRefundRepository
                .findByAdmissionIdAndIsDeletedFalseOrderByRefundDateDesc(admissionId)
                .stream()
                .map(r -> toRefundResponseDTO(r, admission))
                .toList();
    }

    // ==================== STATUS UPDATE ====================

    @Transactional
    public void updateFeeStatus(FeeStatusUpdateDTO updateDTO) {
        log.debug("Updating fee status for admission: {}", updateDTO.getAdmissionId());

        Admission admission = admissionRepository.findById(updateDTO.getAdmissionId())
                .filter(a -> !a.getIsDeleted())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Admission not found: " + updateDTO.getAdmissionId()));

        admission.setStatus(updateDTO.getPaymentStatus());
        admissionRepository.save(admission);

        log.info("Updated fee status to: {}", updateDTO.getPaymentStatus());
    }

    // ==================== HELPER METHODS ====================

    private String generateReceiptNumber() {
        String prefix = "REC" + Year.now().getValue();
        String maxReceiptNo = feeReceiptRepository.findMaxReceiptNumber(prefix);

        int nextNumber = 1;
        if (maxReceiptNo != null && maxReceiptNo.length() > prefix.length()) {
            try {
                nextNumber = Integer.parseInt(maxReceiptNo.substring(prefix.length())) + 1;
            } catch (NumberFormatException e) {
                log.warn("Error parsing receipt number: {}", maxReceiptNo);
            }
        }

        return String.format("%s%05d", prefix, nextNumber);
    }

    private String generateInvoiceNumber() {
        String prefix = "INV" + Year.now().getValue();
        return String.format("%s%05d", prefix, new Random().nextInt(99999));
    }

    private String generateRefundNumber() {
        String prefix = "REF" + Year.now().getValue();
        String maxRefundNo = feeRefundRepository.findMaxRefundNumber(prefix);

        int nextNumber = 1;
        if (maxRefundNo != null && maxRefundNo.length() > prefix.length()) {
            try {
                nextNumber = Integer.parseInt(maxRefundNo.substring(prefix.length())) + 1;
            } catch (NumberFormatException e) {
                log.warn("Error parsing refund number: {}", maxRefundNo);
            }
        }

        return String.format("%s%05d", prefix, nextNumber);
    }

    private void updateInstallmentStatus(Long installmentId, String status) {
        feeInstallmentRepository.findById(installmentId).ifPresent(installment -> {
            installment.setStatus(status);
            feeInstallmentRepository.save(installment);
        });
    }

    private FeeReceiptResponseDTO toReceiptResponseDTO(FeeReceipt receipt, Admission admission) {
        return FeeReceiptResponseDTO.builder()
                .id(receipt.getId())
                .receiptNumber(receipt.getReceiptNumber())
                .invoiceNumber(receipt.getInvoiceNumber())
                .admissionId(receipt.getAdmissionId())
                .studentName(admission.getFullName())
                .registrationNumber(admission.getRegistrationNumber())
                .installmentId(receipt.getInstallmentId())
                .receiptDate(receipt.getReceiptDate())
                .amountReceived(receipt.getAmountReceived())
                .previousPaid(receipt.getPreviousPaid())
                .totalFees(receipt.getTotalFees())
                .pendingFees(receipt.getPendingFees())
                .gstEnabled(receipt.getGstEnabled())
                .sgstPercent(receipt.getSgstPercent())
                .cgstPercent(receipt.getCgstPercent())
                .invoiceValue(receipt.getInvoiceValue())
                .paymentMode(receipt.getPaymentMode())
                .bankName(receipt.getBankName())
                .chequeNumber(receipt.getChequeNumber())
                .chequeDate(receipt.getChequeDate())
                .transactionNumber(receipt.getTransactionNumber())
                .ifscCode(receipt.getIfscCode())
                .onlinePaymentMode(receipt.getOnlinePaymentMode())
                .nextDueDate(receipt.getNextDueDate())
                .receiptType(receipt.getReceiptType())
                .notes(receipt.getNotes())
                .status(receipt.getStatus())
                .build();
    }

    private FeeRefundResponseDTO toRefundResponseDTO(FeeRefund refund, Admission admission) {
        return FeeRefundResponseDTO.builder()
                .id(refund.getId())
                .refundNumber(refund.getRefundNumber())
                .admissionId(refund.getAdmissionId())
                .studentName(admission.getFullName())
                .registrationNumber(admission.getRegistrationNumber())
                .refundDate(refund.getRefundDate())
                .refundAmount(refund.getRefundAmount())
                .totalFees(refund.getTotalFees())
                .paidFees(refund.getPaidFees())
                .pendingFees(refund.getPendingFees())
                .paymentMode(refund.getPaymentMode())
                .bankName(refund.getBankName())
                .chequeNumber(refund.getChequeNumber())
                .chequeDate(refund.getChequeDate())
                .transactionNumber(refund.getTransactionNumber())
                .ifscCode(refund.getIfscCode())
                .onlinePaymentMode(refund.getOnlinePaymentMode())
                .paymentClear(refund.getPaymentClear())
                .notes(refund.getNotes())
                .status(refund.getStatus())
                .build();
    }

    @Transactional(readOnly = true)
    public Page<FeesSummaryDTO> searchFees(FeesSearchDTO searchDTO) {
        log.debug("Searching fees with criteria: {}", searchDTO);

        // Create sort
        Sort sort = Sort.by(
                searchDTO.getSortDirection().equalsIgnoreCase("ASC")
                        ? Sort.Direction.ASC
                        : Sort.Direction.DESC,
                searchDTO.getSortBy()
        );

        Pageable pageable = PageRequest.of(
                searchDTO.getPage(),
                searchDTO.getSize(),
                sort
        );

        // Build specification
        Specification<Fees> spec = buildFeesSpecification(searchDTO);

        // Execute query
        Page<Fees> fees = feesRepository.findAll(spec, pageable);

        return fees.map(this::toFeesSummaryDTO);
    }

    /**
     * Build dynamic specification based on search criteria
     */
    private Specification<Fees> buildFeesSpecification(FeesSearchDTO searchDTO) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Always exclude deleted records
            predicates.add(cb.isFalse(root.get("isDeleted")));

            // Search term (registration number, student name, or mobile)
            if (searchDTO.getSearchTerm() != null && !searchDTO.getSearchTerm().trim().isEmpty()) {
                String searchPattern = "%" + searchDTO.getSearchTerm().toLowerCase().trim() + "%";
                Predicate regNo = cb.like(cb.lower(root.get("registrationNumber")), searchPattern);
                Predicate name = cb.like(cb.lower(root.get("studentName")), searchPattern);
                Predicate mobile = cb.like(root.get("mobile"), searchPattern);
                predicates.add(cb.or(regNo, name, mobile));
            }

            // Status filter
            if (searchDTO.getStatus() != null && !searchDTO.getStatus().trim().isEmpty()) {
                predicates.add(cb.equal(root.get("status"), searchDTO.getStatus()));
            }

            // Course filter
            if (searchDTO.getCourse() != null && !searchDTO.getCourse().trim().isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("course")),
                        "%" + searchDTO.getCourse().toLowerCase() + "%"));
            }

            // Due date range
            if (searchDTO.getDueDateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dueDate"), searchDTO.getDueDateFrom()));
            }
            if (searchDTO.getDueDateTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dueDate"), searchDTO.getDueDateTo()));
            }

            // Total fees range
            if (searchDTO.getMinTotalFees() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("totalFees"), searchDTO.getMinTotalFees()));
            }
            if (searchDTO.getMaxTotalFees() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("totalFees"), searchDTO.getMaxTotalFees()));
            }

            // Fees due range
            if (searchDTO.getMinFeesDue() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("feesDue"), searchDTO.getMinFeesDue()));
            }
            if (searchDTO.getMaxFeesDue() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("feesDue"), searchDTO.getMaxFeesDue()));
            }

            // Overdue filter
            if (searchDTO.getOverdue() != null && searchDTO.getOverdue()) {
                predicates.add(cb.and(
                        cb.equal(root.get("status"), "Pending"),
                        cb.lessThan(root.get("dueDate"), LocalDate.now())
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

}