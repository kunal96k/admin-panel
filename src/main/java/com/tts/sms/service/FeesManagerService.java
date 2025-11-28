package com.tts.sms.service;

import com.tts.sms.dto.*;
import com.tts.sms.exception.ResourceNotFoundException;
import com.tts.sms.model.*;
import com.tts.sms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.Year;
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

    // ==================== FEES SUMMARY ====================

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

    // ==================== CSV IMPORT - PRESERVE NULL VALUES ====================

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
                if (dto.getRegistrationNumber() == null || dto.getRegistrationNumber().isEmpty()) {
                    errors.add(FeesBulkImportResponseDTO.ImportError.builder()
                            .rowNumber(rowNumber)
                            .fieldName("registrationNumber")
                            .errorMessage("Registration number is required")
                            .rejectedValue(dto.getRegistrationNumber())
                            .build());
                    continue;
                }

                Optional<Fees> existingFees = feesRepository
                        .findByRegistrationNumberAndIsDeletedFalse(dto.getRegistrationNumber());

                Fees fees;
                if (existingFees.isPresent()) {
                    fees = existingFees.get();
                    fees.setStudentName(dto.getStudentName());
                    fees.setMobile(dto.getMobile());
                    fees.setTotalFees(dto.getTotalFees() != null ? dto.getTotalFees() : 0.0);
                    fees.setFeesDue(dto.getFeesDue() != null ? dto.getFeesDue() : 0.0);
                    fees.setTotalPaid(dto.getTotalPaid() != null ? dto.getTotalPaid() : 0.0);
                    fees.setDueDate(dto.getDueDate()); // KEEP NULL IF NULL
                    fees.setFeesRefund(dto.getFeesRefund() != null ? dto.getFeesRefund() : 0.0);
                    fees.setStatus(dto.getStatus() != null ? dto.getStatus() : "Pending");
                    fees.setCourse(dto.getCourse());
                    fees.setUpdatedBy("CSV_IMPORT");
                    updateCount++;
                } else {
                    fees = Fees.builder()
                            .registrationNumber(dto.getRegistrationNumber())
                            .studentName(dto.getStudentName())
                            .mobile(dto.getMobile())
                            .totalFees(dto.getTotalFees() != null ? dto.getTotalFees() : 0.0)
                            .feesDue(dto.getFeesDue() != null ? dto.getFeesDue() : 0.0)
                            .totalPaid(dto.getTotalPaid() != null ? dto.getTotalPaid() : 0.0)
                            .dueDate(dto.getDueDate()) // KEEP NULL IF NULL
                            .feesRefund(dto.getFeesRefund() != null ? dto.getFeesRefund() : 0.0)
                            .status(dto.getStatus() != null ? dto.getStatus() : "Pending")
                            .course(dto.getCourse())
                            .createdBy("CSV_IMPORT")
                            .build();
                    createCount++;
                }

                Admission admission = admissionRepository
                        .findByRegistrationNumberAndIsDeletedFalse(dto.getRegistrationNumber());
                if (admission != null) {
                    fees.setAdmissionId(admission.getId());
                }

                feesRepository.save(fees);
                successCount++;

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

    // ==================== FEE RECEIPTS - USE REG NO ====================

    @Transactional
    public FeeReceiptResponseDTO createFeeReceipt(FeeReceiptRequestDTO requestDTO) {
        log.debug("Creating fee receipt for regNo: {}", requestDTO.getRegNo());

        Admission admission = admissionRepository
                .findByRegistrationNumberAndIsDeletedFalse(requestDTO.getRegNo());

        if (admission == null) {
            throw new ResourceNotFoundException("Admission not found: " + requestDTO.getRegNo());
        }

        FeeReceipt receipt = FeeReceipt.builder()
                .receiptNumber(generateReceiptNumber())
                .invoiceNumber(generateInvoiceNumber())
                .registrationNumber(requestDTO.getRegNo()) // CHANGED
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
    public List<FeeReceiptResponseDTO> getReceiptsByRegNo(String regNo) {
        log.debug("Fetching receipts for regNo: {}", regNo);

        Admission admission = admissionRepository.findByRegistrationNumberAndIsDeletedFalse(regNo);
        if (admission == null) {
            throw new ResourceNotFoundException("Admission not found: " + regNo);
        }

        return feeReceiptRepository
                .findByRegistrationNumberAndIsDeletedFalseOrderByReceiptDateDesc(regNo) // CHANGED
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

    // ==================== FEE REFUNDS - USE REG NO ====================

    @Transactional
    public FeeRefundResponseDTO createFeeRefund(FeeRefundRequestDTO requestDTO) {
        log.debug("Creating fee refund for regNo: {}", requestDTO.getRegNo());

        Admission admission = admissionRepository.findByRegistrationNumberAndIsDeletedFalse(requestDTO.getRegNo());
        if (admission == null) {
            throw new ResourceNotFoundException("Admission not found: " + requestDTO.getRegNo());
        }

        FeeRefund refund = FeeRefund.builder()
                .refundNumber(generateRefundNumber())
                .registrationNumber(requestDTO.getRegNo()) // CHANGED
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
    public List<FeeRefundResponseDTO> getRefundsByRegNo(String regNo) {
        log.debug("Fetching refunds for regNo: {}", regNo);

        Admission admission = admissionRepository.findByRegistrationNumberAndIsDeletedFalse(regNo);
        if (admission == null) {
            throw new ResourceNotFoundException("Admission not found: " + regNo);
        }

        return feeRefundRepository
                .findByRegistrationNumberAndIsDeletedFalseOrderByRefundDateDesc(regNo) // CHANGED
                .stream()
                .map(r -> toRefundResponseDTO(r, admission))
                .toList();
    }

    // ==================== INSTALLMENTS - USE REG NO ====================

    @Transactional(readOnly = true)
    public List<FeeInstallmentDTO> getInstallmentsByRegNo(String regNo) {
        log.debug("Fetching installments for regNo: {}", regNo);

        Admission admission = admissionRepository.findByRegistrationNumberAndIsDeletedFalse(regNo);
        if (admission == null) {
            throw new ResourceNotFoundException("Admission not found: " + regNo);
        }

        return feeInstallmentRepository
                .findByRegistrationNumberOrderByDueDateAsc(regNo) // CHANGED
                .stream()
                .map(this::toInstallmentDTO)
                .toList();
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

    private FeeInstallmentDTO toInstallmentDTO(FeeInstallment installment) {
        return FeeInstallmentDTO.builder()
                .id(installment.getId())
                .registrationNumber(installment.getRegistrationNumber()) // CHANGED
                .installmentNumber(installment.getInstallmentNumber())
                .dueDate(installment.getDueDate())
                .amount(installment.getAmount())
                .status(installment.getStatus())
                .paidAmount(installment.getPaidAmount())
                .paidDate(installment.getPaidDate())
                .build();
    }

    private FeeReceiptResponseDTO toReceiptResponseDTO(FeeReceipt receipt, Admission admission) {
        return FeeReceiptResponseDTO.builder()
                .id(receipt.getId())
                .receiptNumber(receipt.getReceiptNumber())
                .invoiceNumber(receipt.getInvoiceNumber())
                .registrationNumber(receipt.getRegistrationNumber()) // CHANGED
                .studentName(admission.getFullName())
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
                .registrationNumber(refund.getRegistrationNumber()) // CHANGED
                .studentName(admission.getFullName())
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
}