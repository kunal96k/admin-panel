package com.tts.sms.service;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import com.tts.sms.dto.*;
import com.tts.sms.exception.ResourceNotFoundException;
import com.tts.sms.model.*;
import com.tts.sms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeesManagerService {

    private final AdmissionRepository admissionRepository;
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

    // ==================== FEES SUMMARY ====================

    @Transactional(readOnly = true)
    public Page<FeesSummaryDTO> getAllFeesSummary(int page, int size) {
        log.debug("Fetching fees summary - page: {}, size: {}", page, size);

        Pageable pageable = PageRequest.of(page, size, Sort.by("admissionDate").descending());
        Page<Admission> admissions = admissionRepository.findByIsDeletedFalse(pageable);

        // FIX: Batch fetch all receipt/refund totals in ONE query instead of N queries
        List<Long> admissionIds = admissions.getContent().stream()
                .map(Admission::getId)
                .collect(Collectors.toList());

        // Single query for all receipts
        Map<Long, Double> totalPaidMap = feeReceiptRepository
                .findByAdmissionIdInAndIsDeletedFalse(admissionIds)
                .stream()
                .collect(Collectors.groupingBy(
                        FeeReceipt::getAdmissionId,
                        Collectors.summingDouble(FeeReceipt::getAmountReceived)
                ));

        // Single query for all refunds
        Map<Long, Double> totalRefundMap = feeRefundRepository
                .findByAdmissionIdInAndIsDeletedFalse(admissionIds)
                .stream()
                .collect(Collectors.groupingBy(
                        FeeRefund::getAdmissionId,
                        Collectors.summingDouble(FeeRefund::getRefundAmount)
                ));

        // Single query for all installments
        Map<Long, List<FeeInstallment>> installmentsMap = feeInstallmentRepository
                .findByAdmissionIdIn(admissionIds)
                .stream()
                .collect(Collectors.groupingBy(FeeInstallment::getAdmissionId));

        // Now build DTOs using pre-fetched data (NO MORE QUERIES!)
        return admissions.map(admission -> buildFeesSummaryDTOOptimized(
                admission,
                totalPaidMap.getOrDefault(admission.getId(), 0.0),
                totalRefundMap.getOrDefault(admission.getId(), 0.0),
                installmentsMap.getOrDefault(admission.getId(), Collections.emptyList())
        ));
    }

    private FeesSummaryDTO buildFeesSummaryDTOOptimized(
            Admission admission,
            Double totalPaid,
            Double totalRefund,
            List<FeeInstallment> installments) {

        // Calculate fees
        Double totalFees = admission.getTotalReceivableFees() != null ? admission.getTotalReceivableFees() : 0.0;
        Double feesDue = totalFees - totalPaid + totalRefund;

        // Process installments
        int totalInstallments = installments.size();
        int paidInstallments = (int) installments.stream()
                .filter(i -> "Paid".equals(i.getStatus()))
                .count();
        int pendingInstallments = totalInstallments - paidInstallments;

        // Get next due date
        LocalDate dueDate = installments.stream()
                .filter(i -> "Pending".equals(i.getStatus()))
                .map(FeeInstallment::getDueDate)
                .min(LocalDate::compareTo)
                .orElse(null);

        // Determine status
        String status = feesDue <= 0 ? "Clear" : "Pending";

        return FeesSummaryDTO.builder()
                .admissionId(admission.getId())
                .registrationNumber(admission.getRegistrationNumber())
                .studentName(admission.getFullName())
                .mobile(admission.getMobilePrimary())
                .course(admission.getCourses() != null && !admission.getCourses().isEmpty()
                        ? String.join(", ", admission.getCourses())
                        : "N/A")
                .totalFees(totalFees)
                .totalPaid(totalPaid)
                .feesDue(feesDue)
                .feesRefund(totalRefund)
                .dueDate(dueDate)
                .status(status)
                .totalInstallments(totalInstallments)
                .paidInstallments(paidInstallments)
                .pendingInstallments(pendingInstallments)
                .build();
    }

    @Transactional(readOnly = true)
    public Page<FeesSummaryDTO> searchFees(FeesSearchDTO searchDTO) {
        log.debug("Searching fees with criteria: {}", searchDTO);

        Pageable pageable = PageRequest.of(
                searchDTO.getPage(),
                searchDTO.getSize(),
                Sort.by(
                        "DESC".equalsIgnoreCase(searchDTO.getSortDirection())
                                ? Sort.Direction.DESC
                                : Sort.Direction.ASC,
                        searchDTO.getSortBy()
                )
        );

        // Get all admissions (we'll filter in memory for simplicity)
        Page<Admission> admissions = admissionRepository.findByIsDeletedFalse(pageable);

        List<FeesSummaryDTO> filtered = admissions.getContent().stream()
                .map(this::buildFeesSummaryDTO)
                .filter(dto -> matchesSearchCriteria(dto, searchDTO))
                .collect(Collectors.toList());

        return new PageImpl<>(filtered, pageable, filtered.size());
    }

    private boolean matchesSearchCriteria(FeesSummaryDTO dto, FeesSearchDTO search) {
        if (search.getSearchTerm() != null && !search.getSearchTerm().isEmpty()) {
            String term = search.getSearchTerm().toLowerCase();
            if (!dto.getRegistrationNumber().toLowerCase().contains(term) &&
                    !dto.getStudentName().toLowerCase().contains(term) &&
                    !dto.getMobile().contains(term)) {
                return false;
            }
        }

        if (search.getStatus() != null && !search.getStatus().equals(dto.getStatus())) {
            return false;
        }

        if (search.getCourse() != null && !dto.getCourse().toLowerCase().contains(search.getCourse().toLowerCase())) {
            return false;
        }

        if (search.getDueDateFrom() != null && dto.getDueDate() != null &&
                dto.getDueDate().isBefore(search.getDueDateFrom())) {
            return false;
        }

        if (search.getDueDateTo() != null && dto.getDueDate() != null &&
                dto.getDueDate().isAfter(search.getDueDateTo())) {
            return false;
        }

        return true;
    }

    private FeesSummaryDTO buildFeesSummaryDTO(Admission admission) {
        // Calculate fees
        Double totalFees = admission.getTotalReceivableFees() != null ? admission.getTotalReceivableFees() : 0.0;

        Double totalPaid = feeReceiptRepository.getTotalReceivedByAdmission(admission.getId());
        totalPaid = totalPaid != null ? totalPaid : 0.0;

        Double totalRefund = feeRefundRepository.getTotalRefundByAdmission(admission.getId());
        totalRefund = totalRefund != null ? totalRefund : 0.0;

        Double feesDue = totalFees - totalPaid + totalRefund;

        // Get installments info
        List<FeeInstallment> installments = feeInstallmentRepository
                .findByAdmissionIdOrderByDueDateAsc(admission.getId());

        int totalInstallments = installments.size();
        int paidInstallments = (int) installments.stream()
                .filter(i -> "Paid".equals(i.getStatus()))
                .count();
        int pendingInstallments = totalInstallments - paidInstallments;

        // Get next due date
        LocalDate dueDate = installments.stream()
                .filter(i -> "Pending".equals(i.getStatus()))
                .map(FeeInstallment::getDueDate)
                .min(LocalDate::compareTo)
                .orElse(null);

        // Determine status
        String status = feesDue <= 0 ? "Clear" : "Pending";

        return FeesSummaryDTO.builder()
                .admissionId(admission.getId())
                .registrationNumber(admission.getRegistrationNumber())
                .studentName(admission.getFullName())
                .mobile(admission.getMobilePrimary())
                .course(admission.getCourses() != null && !admission.getCourses().isEmpty()
                        ? String.join(", ", admission.getCourses())
                        : "N/A")
                .totalFees(totalFees)
                .totalPaid(totalPaid)
                .feesDue(feesDue)
                .feesRefund(totalRefund)
                .dueDate(dueDate)
                .status(status)
                .totalInstallments(totalInstallments)
                .paidInstallments(paidInstallments)
                .pendingInstallments(pendingInstallments)
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

        // Update installment status if provided
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

        List<FeeReceipt> receipts = feeReceiptRepository
                .findByAdmissionIdAndIsDeletedFalseOrderByReceiptDateDesc(admissionId);

        return receipts.stream()
                .map(r -> toReceiptResponseDTO(r, admission))
                .collect(Collectors.toList());
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

        List<FeeRefund> refunds = feeRefundRepository
                .findByAdmissionIdAndIsDeletedFalseOrderByRefundDateDesc(admissionId);

        return refunds.stream()
                .map(r -> toRefundResponseDTO(r, admission))
                .collect(Collectors.toList());
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

    // ==================== CSV IMPORT ====================

    @Transactional
    public FeesBulkImportResponseDTO bulkImportFeesCSV(MultipartFile file) {
        log.info("🔄 Starting bulk fees CSV import: {}", file.getOriginalFilename());

        try {
            List<FeesCSVImportDTO> dtos = csvService.parseFeesCSV(file);
            return processBulkFeesImport(dtos);

        } catch (Exception e) {
            log.error("Error during bulk fees import", e);
            return FeesBulkImportResponseDTO.builder()
                    .success(false)
                    .totalRecords(0)
                    .successfulImports(0)
                    .failedImports(0)
                    .message("Failed to process CSV: " + e.getMessage())
                    .build();
        }
    }

    private List<FeesCSVImportDTO> parseFeesCSV(MultipartFile file) throws IOException {
        log.info("📥 Parsing fees CSV file");

        List<FeesCSVImportDTO> dtos = new ArrayList<>();

        try (InputStreamReader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVReader csvReader = new CSVReader(reader)) {

            List<String[]> records = csvReader.readAll();

            if (records.isEmpty()) {
                throw new IllegalArgumentException("CSV file is empty");
            }

            // Skip header
            for (int i = 1; i < records.size(); i++) {
                String[] row = records.get(i);

                try {
                    FeesCSVImportDTO dto = FeesCSVImportDTO.builder()
                            .registrationNumber(getValue(row, 0))
                            .studentName(getValue(row, 1))
                            .mobile(getValue(row, 2))
                            .totalFees(parseDouble(getValue(row, 3)))
                            .feesDue(parseDouble(getValue(row, 4)))
                            .totalPaid(parseDouble(getValue(row, 5)))
                            .dueDate(parseDate(getValue(row, 6)))
                            .feesRefund(parseDouble(getValue(row, 7)))
                            .status(getValue(row, 8))
                            .course(getValue(row, 9))
                            .build();

                    dtos.add(dto);

                } catch (Exception e) {
                    log.error("Error parsing row {}: {}", i + 1, e.getMessage());
                    // Create fallback DTO
                    dtos.add(createFallbackFeesDTO(i));
                }
            }

            log.info("✅ Parsed {} fees records", dtos.size());
            return dtos;

        } catch (CsvException e) {
            throw new IOException("CSV parsing error: " + e.getMessage(), e);
        }
    }

    @Transactional
    public FeesBulkImportResponseDTO processBulkFeesImport(List<FeesCSVImportDTO> dtos) {
        log.info("🔄 Processing {} fees records", dtos.size());

        int successCount = 0;
        List<FeesBulkImportResponseDTO.ImportError> errors = new ArrayList<>();

        for (int i = 0; i < dtos.size(); i++) {
            final int rowNumber = i + 2;
            FeesCSVImportDTO dto = dtos.get(i);

            try {
                // Find admission by registration number
                Admission admission = admissionRepository
                        .findByRegistrationNumberAndIsDeletedFalse(dto.getRegistrationNumber());

                if (admission == null) {
                    errors.add(FeesBulkImportResponseDTO.ImportError.builder()
                            .rowNumber(rowNumber)
                            .fieldName("registrationNumber")
                            .errorMessage("Admission not found")
                            .rejectedValue(dto.getRegistrationNumber())
                            .build());
                    continue;
                }

                // Update admission fees (AS-IS from CSV)
                if (dto.getTotalFees() != null) {
                    admission.setTotalReceivableFees(dto.getTotalFees());
                }

                saveAdmissionInNewTransaction(admission);
                successCount++;

                log.info("✅ Row {}: Updated fees for {}", rowNumber, dto.getRegistrationNumber());

            } catch (Exception e) {
                log.error("❌ Row {}: Error - {}", rowNumber, e.getMessage());

                errors.add(FeesBulkImportResponseDTO.ImportError.builder()
                        .rowNumber(rowNumber)
                        .fieldName("processing")
                        .errorMessage(e.getMessage())
                        .rejectedValue(dto.getRegistrationNumber())
                        .build());
            }
        }

        int failedCount = dtos.size() - successCount;

        log.info("📊 IMPORT RESULT: {}/{} updated, {} failed", successCount, dtos.size(), failedCount);

        return FeesBulkImportResponseDTO.builder()
                .success(successCount > 0)
                .totalRecords(dtos.size())
                .successfulImports(successCount)
                .failedImports(failedCount)
                .errors(errors)
                .message(String.format("%d/%d records processed", successCount, dtos.size()))
                .build();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveAdmissionInNewTransaction(Admission admission) {
        admissionRepository.save(admission);
    }

    // ==================== HELPER METHODS ====================

    private String generateReceiptNumber() {
        String prefix = "REC" + Year.now().getValue();
        String maxReceiptNo = feeReceiptRepository.findMaxReceiptNumber(prefix);

        int nextNumber = 1;
        if (maxReceiptNo != null && maxReceiptNo.length() > prefix.length()) {
            try {
                String numberPart = maxReceiptNo.substring(prefix.length());
                nextNumber = Integer.parseInt(numberPart) + 1;
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
                String numberPart = maxRefundNo.substring(prefix.length());
                nextNumber = Integer.parseInt(numberPart) + 1;
            } catch (NumberFormatException e) {
                log.warn("Error parsing refund number: {}", maxRefundNo);
            }
        }

        return String.format("%s%05d", prefix, nextNumber);
    }

    private void updateInstallmentStatus(Long installmentId, String status) {
        FeeInstallment installment = feeInstallmentRepository.findById(installmentId)
                .orElse(null);

        if (installment != null) {
            installment.setStatus(status);
            feeInstallmentRepository.save(installment);
        }
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

    private String getValue(String[] row, int index) {
        if (index >= row.length) return "N/A";
        String value = row[index];
        return (value == null || value.trim().isEmpty()) ? "N/A" : value.trim();
    }

    private Double parseDouble(String value) {
        if (value == null || value.equals("N/A")) return 0.0;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.equals("N/A")) return null;

        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(dateStr, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private FeesCSVImportDTO createFallbackFeesDTO(int rowNumber) {
        return FeesCSVImportDTO.builder()
                .registrationNumber("ERROR_ROW_" + (rowNumber + 1))
                .studentName("Import Error")
                .mobile("N/A")
                .totalFees(0.0)
                .feesDue(0.0)
                .totalPaid(0.0)
                .feesRefund(0.0)
                .status("Error")
                .course("N/A")
                .build();
    }
}