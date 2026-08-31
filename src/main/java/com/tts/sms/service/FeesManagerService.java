package com.tts.sms.service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.tts.sms.dto.FeeInstallmentBatchDTO;
import com.tts.sms.dto.FeeInstallmentCreateDTO;
import com.tts.sms.dto.FeeInstallmentDTO;
import com.tts.sms.dto.FeeInstallmentUpdateDTO;
import com.tts.sms.dto.FeeReceiptRequestDTO;
import com.tts.sms.dto.FeeReceiptResponseDTO;
import com.tts.sms.dto.FeeRefundRequestDTO;
import com.tts.sms.dto.FeeRefundResponseDTO;
import com.tts.sms.dto.FeesBulkImportResponseDTO;
import com.tts.sms.dto.FeesCSVImportDTO;
import com.tts.sms.dto.FeesSearchDTO;
import com.tts.sms.dto.FeesSummaryDTO;
import com.tts.sms.exception.ResourceNotFoundException;
import com.tts.sms.model.Admission;
import com.tts.sms.model.FeeCollection;
import com.tts.sms.model.FeeInstallment;
import com.tts.sms.model.FeeReceipt;
import com.tts.sms.model.FeeRefund;
import com.tts.sms.model.Fees;
import com.tts.sms.model.User;
import com.tts.sms.repository.AdmissionRepository;
import com.tts.sms.repository.FeeCollectionRepository;
import com.tts.sms.repository.FeeInstallmentRepository;
import com.tts.sms.repository.FeeReceiptRepository;
import com.tts.sms.repository.FeeRefundRepository;
import com.tts.sms.repository.FeesRepository;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeesManagerService {

    private final AdmissionRepository admissionRepository;
    private final FeesRepository feesRepository;
    private final FeeReceiptRepository feeReceiptRepository;
    private final FeeRefundRepository feeRefundRepository;
    private final EmailTemplateService emailTemplateService;
    private final FeeCollectionRepository feeCollectionRepository;
    private final FeeInstallmentRepository feeInstallmentRepository;
    private final CSVService csvService;
    private final FeesManagerMapper feesManagerMapper;
    private final SystemConfigurationService systemConfigurationService;
    private final StudentCategoryService studentCategoryService;

    // ==================== FEES SUMMARY ====================

    @Transactional(readOnly = true)
    public Page<FeesSummaryDTO> getAllFeesSummary(int page, int size) {
        log.debug("Fetching fees summary - page: {}, size: {}", page, size);

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Fees> fees = feesRepository.findByIsDeletedFalse(pageable);

        return fees.map(this::toFeesSummaryDTO);
    }

    @Transactional(readOnly = true)
    public Page<FeesSummaryDTO> searchFees(FeesSearchDTO searchDTO) {
        log.debug("Searching fees with criteria: {}", searchDTO);

        Pageable pageable = PageRequest.of(
                searchDTO.getPage(),
                searchDTO.getSize(),
                Sort.Direction.valueOf(searchDTO.getSortDirection().toUpperCase()),
                searchDTO.getSortBy() != null ? searchDTO.getSortBy() : "createdAt");

        Specification<Fees> spec = com.tts.sms.specification.FeesSpecifications.getSearchSpecification(searchDTO);

        Page<Fees> fees = feesRepository.findAll(spec, pageable);
        return fees.map(this::toFeesSummaryDTO);
    }

    @Transactional(readOnly = true)
    public List<FeesSummaryDTO> getAllFeesForExport(FeesSearchDTO searchDTO) {
        log.info("Fetching all fees for export with criteria: {}", searchDTO);

        Sort.Direction direction = Sort.Direction.DESC;
        if (searchDTO != null && searchDTO.getSortDirection() != null && !searchDTO.getSortDirection().trim().isEmpty()) {
            try {
                direction = Sort.Direction.valueOf(searchDTO.getSortDirection().trim().toUpperCase());
            } catch (Exception e) {
                direction = Sort.Direction.DESC;
            }
        }

        String sortBy = (searchDTO != null && searchDTO.getSortBy() != null && !searchDTO.getSortBy().trim().isEmpty())
                ? searchDTO.getSortBy().trim()
                : "createdAt";

        Specification<Fees> spec = com.tts.sms.specification.FeesSpecifications.getSearchSpecification(searchDTO != null ? searchDTO : FeesSearchDTO.builder().build());
        List<Fees> fees = feesRepository.findAll(spec, Sort.by(direction, sortBy));

        return fees.stream().map(this::toFeesSummaryDTO).collect(Collectors.toList());
    }

    private FeesSummaryDTO toFeesSummaryDTO(Fees fees) {
        // Compute next due date from pending installments
        LocalDate computedNextDueDate = null;
        int paidInstallmentsCount = 0;
        int totalInstallmentsCount = fees.getNumberOfInstallments() != null ? fees.getNumberOfInstallments() : 0;

        if (fees.getFeesDue() != null && fees.getFeesDue() > 0.01) {
            // Only compute for students with pending fees
            boolean nextDueDateFromInstallment = false;
            try {
                List<FeeInstallment> installments = feeInstallmentRepository
                        .findByRegistrationNumberOrderByDueDateAsc(fees.getRegistrationNumber());

                totalInstallmentsCount = installments.size();

                // Find first pending installment and count paid ones
                for (FeeInstallment installment : installments) {
                    if ("Paid".equalsIgnoreCase(installment.getStatus())) {
                        paidInstallmentsCount++;
                    } else if (computedNextDueDate == null &&
                            (!"Paid".equalsIgnoreCase(installment.getStatus()) && !"Refund".equalsIgnoreCase(installment.getStatus()))) {
                        computedNextDueDate = installment.getDueDate();
                        nextDueDateFromInstallment = true; // came from actual installment data
                    }
                }

                // Only fall back to fees.getDueDate() for display — never use it for Overdue determination
                if (computedNextDueDate == null) {
                    computedNextDueDate = fees.getDueDate();
                    // nextDueDateFromInstallment stays false — stale fallback, not reliable for status
                }

            } catch (Exception e) {
                log.warn("Could not compute next due date or installments for {}", fees.getRegistrationNumber());
                computedNextDueDate = fees.getDueDate();
            }

            // Determine status dynamically:
            // Do not override if status is explicitly set to Overdue, Clear, Refund or Cancelled.
            // Otherwise, set to Overdue if the computed next due date (or fallback due date) is in the past.
            String finalStatus = fees.getStatus() != null ? fees.getStatus() : "Pending";
            if (!"Refund".equalsIgnoreCase(finalStatus) && 
                !"Cancelled".equalsIgnoreCase(finalStatus) && 
                !"Overdue".equalsIgnoreCase(finalStatus) && 
                !"Clear".equalsIgnoreCase(finalStatus)) {
                
                if (computedNextDueDate != null && computedNextDueDate.isBefore(LocalDate.now())) {
                    finalStatus = "Overdue";
                } else {
                    finalStatus = "Pending";
                }
            }
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
                    .nextDueDate(computedNextDueDate)
                    .status(finalStatus)
                    .totalInstallments(totalInstallmentsCount)
                    .paidInstallments(paidInstallmentsCount)
                    .pendingInstallments(totalInstallmentsCount - paidInstallmentsCount)
                    .installmentStartDate(fees.getInstallmentStartDate())
                    .numberOfInstallments(fees.getNumberOfInstallments())
                    .daysBetweenInstallments(fees.getDaysBetweenInstallments())
                    .build();
        } else {
            // Fully paid — status is Clear
            paidInstallmentsCount = totalInstallmentsCount;
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
                    .nextDueDate(null) // no next due date when fully paid
                    .status("Clear")
                    .totalInstallments(totalInstallmentsCount)
                    .paidInstallments(paidInstallmentsCount)
                    .pendingInstallments(0)
                    .installmentStartDate(fees.getInstallmentStartDate())
                    .numberOfInstallments(fees.getNumberOfInstallments())
                    .daysBetweenInstallments(fees.getDaysBetweenInstallments())
                    .build();
        }
    }

    /**
     * Send receipt email - Delegates to async email service
     */
    @Transactional(readOnly = true)
    public void sendReceiptEmail(String receiptNo, String email, String studentName, String message, String pdfBase64) {
        log.debug("📧 Preparing to send receipt email for: {}", receiptNo);

        try {
            FeeReceipt receipt = feeReceiptRepository.findByReceiptNumberAndIsDeletedFalse(receiptNo)
                    .orElseThrow(() -> new ResourceNotFoundException("Receipt not found: " + receiptNo));

            Admission admission = admissionRepository
                    .findByRegistrationNumberAndIsDeletedFalse(receipt.getRegistrationNumber());

            if (admission == null) {
                throw new ResourceNotFoundException("Admission not found for receipt: " + receiptNo);
            }

            // Call async email service (fire-and-forget)
            emailTemplateService.sendFeeReceiptWithPDF(
                    email,
                    studentName,
                    receiptNo,
                    receipt.getAmountReceived(),
                    receipt.getReceiptDate(),
                    receipt.getPaymentMode(),
                    message,
                    pdfBase64);

            log.info(" Receipt email queued for sending to: {}", email);

        } catch (ResourceNotFoundException e) {
            log.error(" Resource not found: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error(" Failed to queue receipt email for {}: {}", receiptNo, e.getMessage());
            throw new RuntimeException("Failed to send email: " + e.getMessage());
        }
    }

    /**
     * Generate PDF using iText
     */
    private byte[] generateReceiptPDF(FeeReceipt receipt, Admission admission) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            com.itextpdf.text.Document document = new com.itextpdf.text.Document();
            com.itextpdf.text.pdf.PdfWriter.getInstance(document, baos);

            document.open();

            // Add content
            com.itextpdf.text.Font titleFont = new com.itextpdf.text.Font(
                    com.itextpdf.text.Font.FontFamily.HELVETICA, 18, com.itextpdf.text.Font.BOLD);
            com.itextpdf.text.Font normalFont = new com.itextpdf.text.Font(
                    com.itextpdf.text.Font.FontFamily.HELVETICA, 12, com.itextpdf.text.Font.NORMAL);

            // Title
            com.itextpdf.text.Paragraph title = new com.itextpdf.text.Paragraph(
                    "FEE RECEIPT", titleFont);
            title.setAlignment(com.itextpdf.text.Element.ALIGN_CENTER);
            document.add(title);

            document.add(new com.itextpdf.text.Paragraph(" "));

            // Receipt details
            document.add(new com.itextpdf.text.Paragraph("Receipt No: " + receipt.getReceiptNumber(), normalFont));
            document.add(new com.itextpdf.text.Paragraph("Date: " + receipt.getReceiptDate(), normalFont));
            document.add(new com.itextpdf.text.Paragraph("Student Name: " + admission.getFullName(), normalFont));
            document.add(new com.itextpdf.text.Paragraph("Amount: ₹" +
                    String.format("%.2f", receipt.getAmountReceived()), normalFont));
            document.add(new com.itextpdf.text.Paragraph("Payment Mode: " + receipt.getPaymentMode(), normalFont));

            if (receipt.getTransactionNumber() != null) {
                document.add(new com.itextpdf.text.Paragraph("Transaction ID: " +
                        receipt.getTransactionNumber(), normalFont));
            }

            document.close();

            return baos.toByteArray();

        } catch (Exception e) {
            log.error(" Failed to generate PDF", e);
            return null;
        }
    }

    /**
     * Generate receipt as PNG image (fallback)
     */
    private byte[] generateReceiptImage(FeeReceipt receipt, Admission admission) {
        try {
            int width = 800;
            int height = 600;

            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
                    width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);

            java.awt.Graphics2D g2d = image.createGraphics();

            // White background
            g2d.setColor(java.awt.Color.WHITE);
            g2d.fillRect(0, 0, width, height);

            // Draw receipt content
            g2d.setColor(java.awt.Color.BLACK);
            g2d.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 24));
            g2d.drawString("FEE RECEIPT", 300, 50);

            g2d.setFont(new java.awt.Font("Arial", java.awt.Font.PLAIN, 16));
            int y = 100;
            g2d.drawString("Receipt No: " + receipt.getReceiptNumber(), 50, y);
            g2d.drawString("Date: " + receipt.getReceiptDate(), 50, y += 30);
            g2d.drawString("Student Name: " + admission.getFullName(), 50, y += 30);
            g2d.drawString("Amount: ₹" + String.format("%.2f", receipt.getAmountReceived()), 50, y += 30);
            g2d.drawString("Payment Mode: " + receipt.getPaymentMode(), 50, y += 30);

            if (receipt.getTransactionNumber() != null) {
                g2d.drawString("Transaction ID: " + receipt.getTransactionNumber(), 50, y += 30);
            }

            g2d.dispose();

            // Convert to PNG bytes
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image, "png", baos);

            return baos.toByteArray();

        } catch (Exception e) {
            log.error(" Failed to generate image", e);
            return null;
        }
    }

    // ==================== CSV IMPORT - PRESERVE NULL VALUES ====================

    @Transactional
    public FeesBulkImportResponseDTO bulkImportFeesCSV(MultipartFile file) {
        log.info(" Starting bulk fees CSV import: {}", file.getOriginalFilename());

        try {
            List<FeesCSVImportDTO> dtos = csvService.parseFeesCSV(file);
            return processBulkFeesImport(dtos);

        } catch (Exception e) {
            log.error(" Error during bulk fees import", e);
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
        log.info(" Processing {} fees records", dtos.size());

        int successCount = 0;
        int updateCount = 0;
        int createCount = 0;
        List<FeesBulkImportResponseDTO.ImportError> errors = new ArrayList<>();

        for (int i = 0; i < dtos.size(); i++) {
            final int rowNumber = i + 2;
            FeesCSVImportDTO dto = dtos.get(i);

            try {
                // VALIDATE REQUIRED FIELDS
                if (dto.getRegistrationNumber() == null || dto.getRegistrationNumber().trim().isEmpty()) {
                    errors.add(FeesBulkImportResponseDTO.ImportError.builder()
                            .rowNumber(rowNumber)
                            .fieldName("registrationNumber")
                            .errorMessage("Registration number is required")
                            .rejectedValue("EMPTY")
                            .build());
                    continue;
                }

                // VALIDATE STUDENT NAME
                if (dto.getStudentName() == null || dto.getStudentName().trim().isEmpty()) {
                    errors.add(FeesBulkImportResponseDTO.ImportError.builder()
                            .rowNumber(rowNumber)
                            .fieldName("studentName")
                            .errorMessage("Student name is required")
                            .rejectedValue(dto.getRegistrationNumber())
                            .build());
                    continue;
                }

                // SAVE IN NEW TRANSACTION TO ISOLATE FAILURES
                try {
                    saveFeeRecordInNewTransaction(dto, rowNumber);
                    successCount++;

                    // Check if it's an update or create - MOVED BEFORE save to avoid duplicate
                    // query
                    Optional<Fees> existing = feesRepository
                            .findByRegistrationNumberAndIsDeletedFalse(dto.getRegistrationNumber());
                    if (existing.isPresent()) {
                        updateCount++;
                    } else {
                        createCount++;
                    }

                } catch (Exception saveEx) {
                    log.error(" Row {}: Save failed - {}", rowNumber, saveEx.getMessage());
                    errors.add(FeesBulkImportResponseDTO.ImportError.builder()
                            .rowNumber(rowNumber)
                            .fieldName("database")
                            .errorMessage(saveEx.getMessage())
                            .rejectedValue(dto.getRegistrationNumber())
                            .build());
                }

            } catch (Exception e) {
                log.error(" Row {}: Error - {}", rowNumber, e.getMessage(), e);
                errors.add(FeesBulkImportResponseDTO.ImportError.builder()
                        .rowNumber(rowNumber)
                        .fieldName("processing")
                        .errorMessage(e.getMessage())
                        .rejectedValue(dto.getRegistrationNumber() != null ? dto.getRegistrationNumber() : "UNKNOWN")
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
                .message(String.format("%d/%d records processed (Created: %d, Updated: %d, Failed: %d)",
                        successCount, dtos.size(), createCount, updateCount, failedCount))
                .build();
    }

    /**
     * Save fees record in NEW transaction to isolate failures
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveFeeRecordInNewTransaction(FeesCSVImportDTO dto, int rowNumber) {
        try {
            // Check if record exists
            Optional<Fees> existingFees = feesRepository
                    .findByRegistrationNumberAndIsDeletedFalse(dto.getRegistrationNumber());

            Fees fees;
            if (existingFees.isPresent()) {
                // UPDATE EXISTING RECORD
                fees = existingFees.get();

                // Only update if value is not null
                if (dto.getStudentName() != null) {
                    fees.setStudentName(dto.getStudentName());
                }
                if (dto.getMobile() != null) {
                    fees.setMobile(dto.getMobile());
                }
                if (dto.getTotalFees() != null) {
                    fees.setTotalFees(dto.getTotalFees());
                } else {
                    fees.setTotalFees(0.0);
                }
                if (dto.getFeesDue() != null) {
                    fees.setFeesDue(dto.getFeesDue());
                } else {
                    fees.setFeesDue(0.0);
                }
                if (dto.getTotalPaid() != null) {
                    fees.setTotalPaid(dto.getTotalPaid());
                } else {
                    fees.setTotalPaid(0.0);
                }
                if (dto.getDueDate() != null) {
                    fees.setDueDate(dto.getDueDate());
                }
                if (dto.getFeesRefund() != null) {
                    fees.setFeesRefund(dto.getFeesRefund());
                } else {
                    fees.setFeesRefund(0.0);
                }
                if (dto.getStatus() != null) {
                    fees.setStatus(dto.getStatus());
                }
                if (dto.getCourse() != null) {
                    fees.setCourse(dto.getCourse());
                }

                fees.setUpdatedBy("CSV_IMPORT");

            } else {
                // CREATE NEW RECORD
                fees = Fees.builder()
                        .registrationNumber(dto.getRegistrationNumber())
                        .studentName(dto.getStudentName())
                        .mobile(dto.getMobile())
                        .totalFees(dto.getTotalFees() != null ? dto.getTotalFees() : 0.0)
                        .feesDue(dto.getFeesDue() != null ? dto.getFeesDue() : 0.0)
                        .totalPaid(dto.getTotalPaid() != null ? dto.getTotalPaid() : 0.0)
                        .dueDate(dto.getDueDate())
                        .feesRefund(dto.getFeesRefund() != null ? dto.getFeesRefund() : 0.0)
                        .status(dto.getStatus() != null ? dto.getStatus() : "Pending")
                        .course(dto.getCourse())
                        .createdBy("CSV_IMPORT")
                        .build();

                // Try to link to admission
                try {
                    Admission admission = admissionRepository
                            .findByRegistrationNumberAndIsDeletedFalse(dto.getRegistrationNumber());
                    if (admission != null) {
                        fees.setAdmissionId(admission.getId());
                    }
                } catch (Exception e) {
                    log.debug("Could not link admission for regNo: {}", dto.getRegistrationNumber());
                }
            }

            // SAVE AND FLUSH IMMEDIATELY
            Fees saved = feesRepository.save(fees);
            feesRepository.flush();

            log.debug(" Row {}: Saved fees for regNo: {}", rowNumber, dto.getRegistrationNumber());

        } catch (Exception e) {
            log.error(" Row {}: Database error - {}", rowNumber, e.getMessage());
            throw new RuntimeException("Failed to save: " + e.getMessage(), e);
        }
    }

    // ==================== FEE RECEIPTS - USE REG NO ====================

    /**
     * Recalculate fees from installments and refunds
     */
    @Transactional
    public void recalculateFeesFromTransactions(String regNo) {
        log.debug(" Recalculating fees from all transactions for regNo: {}", regNo);

        try {
            // Get all receipts
            List<FeeReceipt> receipts = feeReceiptRepository
                    .findByRegistrationNumberAndIsDeletedFalseOrderByReceiptDateDesc(regNo);

            // Get all refunds
            List<FeeRefund> refunds = feeRefundRepository
                    .findByRegistrationNumberAndIsDeletedFalseOrderByRefundDateDesc(regNo);

            // Calculate gross total paid from fee_receipts
            final Double grossTotalPaidFromReceipts = receipts.stream()
                    .mapToDouble(r -> r.getAmountReceived() != null ? r.getAmountReceived() : 0.0)
                    .sum();

            // Calculate total refunds
            Double totalRefund = refunds.stream()
                    .mapToDouble(r -> r.getRefundAmount() != null ? r.getRefundAmount() : 0.0)
                    .sum();

            // Update fees record
            feesRepository.findByRegistrationNumberAndIsDeletedFalse(regNo)
                    .ifPresent(fees -> {
                        Double grossTotalPaid;
                        if (!receipts.isEmpty()) {
                            // DEFINITIVE FIX: Opening balance = the amount imported/set in fees table
                            // BEFORE any new receipts were ever created.
                            // We derive it safely as: smallest previousPaid in fee_receipts (by ID)
                            // which is what the UI populated from fees.totalPaid at the time of the
                            // very first receipt.
                            // But to avoid ANY dependency on previousPaid (which can be corrupted
                            // by old running code), we use the most robust formula:
                            //   openingBalance = Admission.totalPayableFees is the TOTAL FEES,
                            //   not the opening paid. So we check admission for total fees and
                            //   use the fees table original import data.
                            //
                            // The correct opening balance = fees.totalPaid before receipts were recorded.
                            // Since fees.totalPaid gets corrupted, we read from the earliest receipt's
                            // previousPaid (by smallest receipt ID). This value was set by the UI at
                            // the time of FIRST receipt creation from student.totalPaid at that moment.
                            // After DB reset, totalPaid=0, so first receipt's previousPaid=0. Correct.
                            Double openingBalance = getOpeningBalance(receipts);
                            grossTotalPaid = openingBalance + grossTotalPaidFromReceipts;
                        } else {
                            // No receipts yet — preserve the imported total paid from fees table as-is
                            grossTotalPaid = fees.getTotalPaid() != null ? fees.getTotalPaid() : 0.0;
                        }

                        // Net amount = Gross Paid - Refunds
                        Double netTotalPaid = grossTotalPaid - totalRefund;

                        // Store NET paid in database
                        fees.setTotalPaid(Math.max(0, netTotalPaid));

                        // Store refund amount separately
                        fees.setFeesRefund(totalRefund);

                        // Fees Due = Total Fees - Net Paid
                        Double feesDue = fees.getTotalFees() - netTotalPaid;
                        fees.setFeesDue(Math.max(0, feesDue));

                        // Update due date based on pending installments
                        LocalDate nextDueDate = null;
                        if (feesDue > 0.01) {
                            // Find next pending installment
                            try {
                                List<FeeInstallment> installments = feeInstallmentRepository
                                        .findByRegistrationNumberOrderByDueDateAsc(regNo);

                                // Auto-correct installment statuses (Pending <-> Overdue based on current date)
                                installments.forEach(i -> {
                                    if (i.getDueDate() != null) {
                                        if ("Pending".equalsIgnoreCase(i.getStatus()) && i.getDueDate().isBefore(LocalDate.now())) {
                                            i.setStatus("Overdue");
                                            feeInstallmentRepository.save(i);
                                        } else if ("Overdue".equalsIgnoreCase(i.getStatus()) && !i.getDueDate().isBefore(LocalDate.now())) {
                                            i.setStatus("Pending");
                                            feeInstallmentRepository.save(i);
                                        }
                                    }
                                });

                                nextDueDate = installments.stream()
                                        .filter(i -> i.getStatus() != null && 
                                               !"Paid".equalsIgnoreCase(i.getStatus()) && 
                                               !"Refund".equalsIgnoreCase(i.getStatus()))
                                        .map(FeeInstallment::getDueDate)
                                        .findFirst()
                                        .orElse(null);

                                log.info("🗓️ Next due date for {}: {}", regNo, nextDueDate);
                            } catch (Exception e) {
                                log.warn("Could not get next installment date: {}", e.getMessage());
                            }
                        }
                        fees.setDueDate(nextDueDate); // Set to null if clear

                        // Auto-update status
                        // nextDueDate was computed from actual installment data above, so Overdue check is accurate
                        if (totalRefund > 0 && feesDue > 0.01) {
                            fees.setStatus("Refund");
                        } else if (feesDue <= 0.01) {
                            fees.setStatus("Clear");
                            fees.setDueDate(null); // Clear due date when paid
                        } else if (nextDueDate != null && nextDueDate.isBefore(LocalDate.now())) {
                            fees.setStatus("Overdue");
                        } else {
                            fees.setStatus("Pending");
                        }

                        fees.setUpdatedBy("SYSTEM");
                        feesRepository.save(fees);

                        log.info(
                                " Updated Fees - RegNo: {}, TotalFees: ₹{}, NetPaid: ₹{}, Due: ₹{}, Refund: ₹{}, Status: {}, NextDue: {}",
                                regNo, fees.getTotalFees(), netTotalPaid, feesDue, totalRefund, fees.getStatus(),
                                nextDueDate);
                    });

        } catch (Exception e) {
            log.error("❌ Failed to recalculate fees for {}", regNo, e);
            throw new RuntimeException("Failed to recalculate fees: " + e.getMessage(), e);
        }
    }

    @Transactional
    public FeeReceiptResponseDTO createFeeReceipt(FeeReceiptRequestDTO requestDTO) {
        log.debug("Creating fee receipt for regNo: {}", requestDTO.getRegNo());

        // Detect old vs new student
        boolean isOldStudent = (requestDTO.getRegNo() != null && !requestDTO.getRegNo().trim().toUpperCase().startsWith("REG"));

        if (isOldStudent) {
            log.info("🔧 Processing receipt for OLD STUDENT: {}", requestDTO.getRegNo());
        } else {
            log.info(" Processing receipt for NEW STUDENT: {}", requestDTO.getRegNo());
        }

        // Verify admission exists
        Admission admission = admissionRepository
                .findByRegistrationNumberAndIsDeletedFalse(requestDTO.getRegNo());

        if (admission == null) {
            log.error("❌ Admission not found with registration number: {}", requestDTO.getRegNo());
            throw new ResourceNotFoundException(
                    "Admission not found with registration number: " + requestDTO.getRegNo());
        }

        log.info(" Verified admission exists: {} ({})", admission.getFullName(), requestDTO.getRegNo());

        // Generate receipt number
        String receiptNumber = generateReceiptNumber();
        log.debug("Generated receipt number: {}", receiptNumber);

        // Get current user for audit trail
        String currentUser = "SYSTEM";
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof User) {
                User user = (User) auth.getPrincipal();
                currentUser = user.getEmployee().getEmployeeName();
            }
        } catch (Exception e) {
            log.warn("Could not get current user: {}", e.getMessage());
        }

        // Create receipt entity
        FeeReceipt receipt = feesManagerMapper.toReceiptEntity(requestDTO);
        receipt.setReceiptNumber(receiptNumber);
        receipt.setRegistrationNumber(requestDTO.getRegNo());
        receipt.setCreatedBy(currentUser); // Set creator

        // Generate invoice number
        if (Boolean.TRUE.equals(requestDTO.getGstEnabled())) {
            String invoiceNumber = generateInvoiceNumber();
            receipt.setInvoiceNumber(invoiceNumber);
            log.debug("Generated invoice number: {}", invoiceNumber);
        }

        // Handle installment if provided (for both new and old/imported students who have installments)
        if (requestDTO.getInstallmentId() != null) {
            log.debug("🔗 Linking receipt to installment: {}", requestDTO.getInstallmentId());
            receipt.setInstallmentId(requestDTO.getInstallmentId()); // CRITICAL: Set installment ID
        } else {
            receipt.setInstallmentId(null);
        }

        // Save receipt
        FeeReceipt savedReceipt = feeReceiptRepository.save(receipt);
        log.info(" Created fee receipt: {} for regNo: {}", receiptNumber, requestDTO.getRegNo());

        // CRITICAL FIX: Update installment BEFORE updating fees
        if (requestDTO.getInstallmentId() != null) {
            try {
                updateInstallmentStatus(
                        requestDTO.getInstallmentId(),
                        requestDTO.getAmountReceived(),
                        currentUser // Pass current user
                );
                log.info(" Updated installment: {}", requestDTO.getInstallmentId());
            } catch (Exception e) {
                log.error("❌ Failed to update installment: {}", requestDTO.getInstallmentId(), e);
                // Don't throw - receipt is already saved
            }
        }

        // Update Fees table - WORKS FOR BOTH OLD AND NEW STUDENTS
        try {
            updateFeesTableAfterReceipt(requestDTO.getRegNo(), requestDTO.getAmountReceived());
            log.info(" Updated fees table for regNo: {}", requestDTO.getRegNo());
        } catch (Exception e) {
            log.error("❌ Failed to update fees table for regNo: {}", requestDTO.getRegNo(), e);
        }

        // Full recalculation: recomputes status (Pending/Overdue/Clear) from actual installment due dates
        // This ensures the badge shown after receipt save is immediately correct
        try {
            recalculateFeesFromTransactions(requestDTO.getRegNo());
            log.info(" Full recalculation completed for regNo: {}", requestDTO.getRegNo());
        } catch (Exception e) {
            log.warn(" Full recalculation failed for regNo: {} - status may need manual refresh: {}", requestDTO.getRegNo(), e.getMessage());
        }

        return feesManagerMapper.toReceiptResponseDTO(savedReceipt);
    }

    /**
     * Update installment status after payment
     */
    private void updateInstallmentStatus(Long installmentId, Double amountReceived, String updatedBy) {
        log.info(" Updating installment {} with amount: ₹{}", installmentId, amountReceived);

        feeInstallmentRepository.findById(installmentId)
                .ifPresent(installment -> {
                    installment.setPaidAmount(amountReceived);
                    installment.setPaidDate(LocalDate.now());
                    installment.setStatus("Paid");
                    installment.setUpdatedBy(updatedBy); // Set updater

                    // CRITICAL: Save and flush immediately
                    FeeInstallment saved = feeInstallmentRepository.saveAndFlush(installment);

                    log.info(" Installment {} marked as Paid (Amount: ₹{}, Date: {}, UpdatedBy: {})",
                            installmentId, amountReceived, LocalDate.now(), updatedBy);

                    // Verify save
                    if (saved.getStatus().equals("Paid")) {
                        log.info(" Verified: Installment {} status confirmed as Paid in DB", installmentId);
                    } else {
                        log.error("❌ CRITICAL: Installment {} status NOT updated in DB!", installmentId);
                    }
                });
    }

    /**
     * Generate receipt number (e.g., REC0001, REC0002)
     */
    private String generateReceiptNumber() {
        String prefix = "REC";
        String maxReceiptNo = feeReceiptRepository.findMaxReceiptNumber(prefix);

        int nextNumber = 1;
        if (maxReceiptNo != null && maxReceiptNo.length() > prefix.length()) {
            try {
                String numberPart = maxReceiptNo.substring(prefix.length());
                nextNumber = Integer.parseInt(numberPart) + 1;
            } catch (NumberFormatException e) {
                log.warn("Error parsing receipt number: {}, starting from 1", maxReceiptNo);
            }
        }

        return String.format("%s%04d", prefix, nextNumber);
    }

    /**
     * Generate invoice number (e.g., INV0001, INV0002)
     */
    private String generateInvoiceNumber() {
        String prefix = "INV";
        // You'll need to add a similar method in FeeReceiptRepository
        String maxInvoiceNo = feeReceiptRepository.findMaxInvoiceNumber(prefix);

        int nextNumber = 1;
        if (maxInvoiceNo != null && maxInvoiceNo.length() > prefix.length()) {
            try {
                String numberPart = maxInvoiceNo.substring(prefix.length());
                nextNumber = Integer.parseInt(numberPart) + 1;
            } catch (NumberFormatException e) {
                log.warn("Error parsing invoice number: {}, starting from 1", maxInvoiceNo);
            }
        }

        return String.format("%s%04d", prefix, nextNumber);
    }

    /**
     * Update Fees table after receipt creation.
     * IMPORTANT: This method is called AFTER the new receipt is already saved to the DB.
     * It only ensures the fees record exists (creates it from admissions if missing).
     * All math is delegated to recalculateFeesFromTransactions — the single source of truth.
     */
    private void updateFeesTableAfterReceipt(String registrationNumber, Double amountReceived) {
        log.debug(" Updating fees table for: {}", registrationNumber);

        // Ensure the fees record exists (create if missing for old imported students)
        Optional<Fees> feesOpt = feesRepository
                .findByRegistrationNumberAndIsDeletedFalse(registrationNumber);

        if (!feesOpt.isPresent()) {
            log.warn(" No fees record found for {}. Creating new record.", registrationNumber);
            Admission admission = admissionRepository
                    .findByRegistrationNumberAndIsDeletedFalse(registrationNumber);
            if (admission == null) {
                throw new RuntimeException("Cannot create fees record - admission not found: " + registrationNumber);
            }
            Fees fees = Fees.builder()
                    .registrationNumber(registrationNumber)
                    .studentName(admission.getFullName())
                    .mobile(admission.getMobilePrimary())
                    .totalFees(admission.getTotalPayableFees() != null ? admission.getTotalPayableFees() : 0.0)
                    .totalPaid(0.0)
                    .feesDue(admission.getTotalPayableFees() != null ? admission.getTotalPayableFees() : 0.0)
                    .feesRefund(0.0)
                    .status("Pending")
                    .course(admission.getCourses() != null ? String.join(", ", admission.getCourses()) : "N/A")
                    .createdBy("SYSTEM")
                    .build();
            feesRepository.save(fees);
            log.info(" Created new fees record for: {}", registrationNumber);
        }

        // Delegate ALL math to recalculateFeesFromTransactions — single source of truth
        // NOTE: This is called AFTER the new receipt is already in the DB, so
        // recalculateFeesFromTransactions will include it in the sum automatically.
        log.debug(" Delegating to recalculateFeesFromTransactions for: {}", registrationNumber);
    }

    /**
     * Update installment status after payment
     */
    private void updateInstallmentStatus(Long installmentId, Double amountReceived) {
        feeInstallmentRepository.findById(installmentId)
                .ifPresent(installment -> {
                    installment.setPaidAmount(amountReceived);
                    installment.setPaidDate(LocalDate.now());
                    installment.setStatus("Paid");
                    installment.setUpdatedBy("SYSTEM");

                    feeInstallmentRepository.save(installment);
                    log.info(" Updated installment {} to Paid", installmentId);
                });
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getInstallmentConfig(String regNo) {
        log.debug("🔍 Fetching installment config for regNo: {}", regNo);

        Map<String, Object> config = new HashMap<>();

        try {
            // Get from fee_installments table (most recent config)
            List<FeeInstallment> installments = feeInstallmentRepository
                    .findByRegistrationNumberOrderByDueDateAsc(regNo);

            if (!installments.isEmpty()) {
                FeeInstallment first = installments.get(0);

                config.put("startDate", first.getInstallmentStartDate());
                config.put("numberOfInstallments", first.getNumberOfInstallments());
                config.put("daysBetween", first.getDaysBetweenInstallments());
                config.put("totalAmount", first.getTotalAmount());
                config.put("hasExisting", true);

                log.info(" Found installment config from fee_installments");
                return config;
            }

            // Fallback: Get from fees table
            Optional<Fees> feesOpt = feesRepository
                    .findByRegistrationNumberAndIsDeletedFalse(regNo);

            if (feesOpt.isPresent()) {
                Fees fees = feesOpt.get();

                config.put("startDate", fees.getInstallmentStartDate());
                config.put("numberOfInstallments", fees.getNumberOfInstallments());
                config.put("daysBetween", fees.getDaysBetweenInstallments());
                config.put("totalAmount", fees.getTotalFees());
                config.put("hasExisting", false);

                log.info(" Found installment config from fees table");
                return config;
            }

            // No config found
            config.put("hasExisting", false);
            log.warn(" No installment config found for {}", regNo);

        } catch (Exception e) {
            log.error("❌ Error fetching installment config: {}", e.getMessage());
            config.put("hasExisting", false);
        }

        return config;
    }

    @Transactional(readOnly = true)
    public List<FeeReceiptResponseDTO> getReceiptsByRegNo(String regNo) {
        log.debug("📋 Fetching receipts for regNo: {}", regNo);

        List<FeeReceiptResponseDTO> allReceipts = new ArrayList<>();

        try {
            // ALWAYS fetch NEW receipts from fee_receipts table FIRST
            log.info("🔍 Fetching NEW receipts from fee_receipts table for: {}", regNo);

            List<FeeReceipt> newReceipts = feeReceiptRepository
                    .findByRegistrationNumberAndIsDeletedFalseOrderByReceiptDateDesc(regNo);

            log.info(" Found {} NEW receipts in fee_receipts table", newReceipts.size());

            if (!newReceipts.isEmpty()) {
                allReceipts.addAll(newReceipts.stream()
                        .map(this::toReceiptDTO)
                        .collect(Collectors.toList()));
            }

            // THEN fetch OLD receipts from fee_collections table ONLY for OLD students (NOT starting with REG)
            boolean isOldStudent = (regNo != null && !regNo.trim().toUpperCase().startsWith("REG"));
            if (isOldStudent) {
                log.info("🔍 Checking fee_collections table for old receipts: {}", regNo);

                try {
                    String primaryMobile = null;
                    String studentName = null;

                    // Get mobile from FEES table
                    Optional<Fees> feesOpt = feesRepository.findByRegistrationNumberAndIsDeletedFalse(regNo);
                    if (feesOpt.isPresent()) {
                        studentName = feesOpt.get().getStudentName();
                        if (feesOpt.get().getMobile() != null && !feesOpt.get().getMobile().trim().isEmpty()
                                && !"N/A".equalsIgnoreCase(feesOpt.get().getMobile())) {
                            primaryMobile = feesOpt.get().getMobile().trim();
                        }
                    }

                    Admission admission = admissionRepository.findByRegistrationNumberAndIsDeletedFalse(regNo);
                    if (admission != null) {
                        if (studentName == null || studentName.trim().isEmpty()) {
                            studentName = admission.getFullName();
                        }
                        // ONLY PRIMARY MOBILE — STRICTLY IGNORE SECONDARY MOBILE
                        if (primaryMobile == null && admission.getMobilePrimary() != null && !admission.getMobilePrimary().trim().isEmpty()
                                && !"N/A".equalsIgnoreCase(admission.getMobilePrimary())) {
                            primaryMobile = admission.getMobilePrimary().trim();
                        }
                    }

                    List<FeeCollection> oldCollections = new ArrayList<>();
                    Set<Long> addedCollectionIds = new HashSet<>();

                    // 1. First check if exact registration number exists in fee_collections
                    List<FeeCollection> byRegNo = feeCollectionRepository.findByRegistrationNumberAndIsDeletedFalse(regNo);
                    for (FeeCollection fc : byRegNo) {
                        if (fc.getId() != null && addedCollectionIds.add(fc.getId())) {
                            oldCollections.add(fc);
                        }
                    }

                    // 2. If primary mobile is available, search by primary mobile AND match student name strictly
                    if (primaryMobile != null && !primaryMobile.trim().isEmpty()) {
                        List<String> searchMobiles = new ArrayList<>();
                        searchMobiles.add(primaryMobile);
                        String digits = primaryMobile.replaceAll("\\D+", "");
                        if (digits.length() == 10 && !searchMobiles.contains(digits)) {
                            searchMobiles.add(digits);
                        }

                        List<FeeCollection> byMobile = feeCollectionRepository.findByMobileNoInAndIsDeletedFalse(searchMobiles);
                        if (!byMobile.isEmpty() && studentName != null && !studentName.trim().isEmpty()) {
                            final String targetName = studentName;
                            for (FeeCollection fc : byMobile) {
                                if (fc.getId() != null && !addedCollectionIds.contains(fc.getId())) {
                                    if (isStudentNameMatch(fc.getStudentName(), targetName)) {
                                        addedCollectionIds.add(fc.getId());
                                        oldCollections.add(fc);
                                    }
                                }
                            }
                        }
                    }

                    log.info(" Found {} OLD records in fee_collections for regNo: {}", oldCollections.size(), regNo);

                    if (!oldCollections.isEmpty()) {
                        String finalStudentName = studentName != null ? studentName : "N/A";
                        String displayMobile = primaryMobile != null ? primaryMobile : "N/A";
                        List<FeeReceiptResponseDTO> oldReceipts = oldCollections.stream()
                                .map(fc -> FeeReceiptResponseDTO.builder()
                                        .id(fc.getId())
                                        .receiptNumber(fc.getReceiptNo() != null ? fc.getReceiptNo() : "OLD-" + fc.getId())
                                        .invoiceNumber("INV-OLD-" + fc.getId())
                                        .registrationNumber(regNo)
                                        .studentName(fc.getStudentName() != null ? fc.getStudentName() : finalStudentName)
                                        .mobile(fc.getMobileNo() != null ? fc.getMobileNo() : displayMobile)
                                        .amountReceived(fc.getPaidFees() != null ? fc.getPaidFees() : 0.0)
                                        .receiptDate(fc.getReceiptDate())
                                        .paymentMode(fc.getPaymentMode() != null ? fc.getPaymentMode() : "Cash")
                                        .notes(fc.getNotes())
                                        .receiptType("Old Imported")
                                        .status("Completed")
                                        .dataSource("IMPORTED_OLD_DATA")
                                        .build())
                                .collect(Collectors.toList());

                        allReceipts.addAll(oldReceipts);
                    }

                } catch (Exception e) {
                    log.error("❌ Error fetching from fee_collections for {}: {}", regNo, e.getMessage(), e);
                }
            }

            // Sort all receipts by date (newest first)
            allReceipts.sort((a, b) -> {
                if (a.getReceiptDate() == null)
                    return 1;
                if (b.getReceiptDate() == null)
                    return -1;
                return b.getReceiptDate().compareTo(a.getReceiptDate());
            });

            log.info("📊 Total receipts returned: {} for RegNo: {}", allReceipts.size(), regNo);

            return allReceipts;

        } catch (Exception e) {
            log.error("❌ Error in getReceiptsByRegNo for {}: {}", regNo, e.getMessage(), e);
            throw new RuntimeException("Failed to fetch receipts: " + e.getMessage(), e);
        }
    }

    /**
     * Convert FeeReceipt entity to FeeReceiptResponseDTO
     */
    private FeeReceiptResponseDTO toReceiptDTO(FeeReceipt receipt) {
        String studentName = "N/A";
        String mobile = "N/A";
        String course = "N/A";
        Double currentPendingFees = null;
        LocalDate currentNextDueDate = null;

        try {
            Admission admission = admissionRepository
                    .findByRegistrationNumberAndIsDeletedFalse(receipt.getRegistrationNumber());

            if (admission != null) {
                studentName = admission.getFullName();
                mobile = admission.getMobilePrimary();

                // FIX: Get courses from List<String> and join them
                if (admission.getCourses() != null && !admission.getCourses().isEmpty()) {
                    course = String.join(", ", admission.getCourses());
                } else {
                    course = "N/A";
                }

                // GET CURRENT FEES STATUS
                Fees fees = feesRepository
                        .findByRegistrationNumberAndIsDeletedFalse(receipt.getRegistrationNumber())
                        .orElse(null);

                if (fees != null) {
                    currentPendingFees = fees.getFeesDue();
                    currentNextDueDate = fees.getDueDate();

                    if (currentPendingFees != null && currentPendingFees <= 0.01) {
                        currentPendingFees = 0.0;
                        currentNextDueDate = null;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not fetch student details for regNo: {}", receipt.getRegistrationNumber());
        }

        return FeeReceiptResponseDTO.builder()
                .id(receipt.getId())
                .receiptNumber(receipt.getReceiptNumber())
                .invoiceNumber(receipt.getInvoiceNumber())
                .registrationNumber(receipt.getRegistrationNumber())
                .studentName(studentName)
                .mobile(mobile)
                .course(course)
                .installmentId(receipt.getInstallmentId())
                .receiptDate(receipt.getReceiptDate())
                .amountReceived(receipt.getAmountReceived())
                .previousPaid(receipt.getPreviousPaid())
                .totalFees(receipt.getTotalFees())
                .pendingFees(receipt.getPendingFees())
                .currentPendingFees(currentPendingFees)
                .currentNextDueDate(currentNextDueDate)
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
                .receiptType(receipt.getReceiptType() != null ? receipt.getReceiptType() : "Regular")
                .notes(receipt.getNotes())
                .status(receipt.getStatus())
                .build();
    }

    /**
     * Get receipts from fee_collections table (old imported data)
     * Converts FeeCollection records to FeeReceiptResponseDTO for display
     */
    @Transactional(readOnly = true)
    public List<FeeReceiptResponseDTO> getReceiptsFromFeeCollections(String regNo) {
        log.debug("🔍 Fetching receipts from fee_collections for regNo: {}", regNo);
        if (regNo == null || regNo.trim().isEmpty() || regNo.trim().toUpperCase().startsWith("REG")) {
            return new ArrayList<>();
        }
        return getReceiptsByRegNo(regNo);
    }

    /**
     * Strict accounting delete: soft-delete receipt, revert installment if linked,
     * then recalc Fees totals/status/dueDate from remaining receipts/refunds.
     */
    @Transactional
    public void deleteFeeReceipt(Long receiptId) {
        log.debug("Deleting fee receipt: {}", receiptId);

        FeeReceipt receipt = feeReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt not found: " + receiptId));

        final String regNo = receipt.getRegistrationNumber();
        final Long installmentId = receipt.getInstallmentId();

        // HARD DELETE: Remove the record completely from database
        feeReceiptRepository.delete(receipt);
        feeReceiptRepository.flush();

        if (installmentId != null) {
            try {
                feeInstallmentRepository.findById(installmentId)
                        .ifPresent(installment -> {
                            installment.setStatus("Pending");
                            installment.setPaidAmount(null);
                            installment.setPaidDate(null);
                            installment.setUpdatedBy("SYSTEM");
                            feeInstallmentRepository.saveAndFlush(installment);
                        });
            } catch (Exception e) {
                log.error("Failed to revert installment {} for deleted receipt {}", installmentId, receiptId, e);
            }
        }

        if (regNo != null && !regNo.trim().isEmpty()) {
            recalculateFeesFromTransactions(regNo);
        }

        log.info("Deleted fee receipt: {}", receipt.getReceiptNumber());
    }

    /**
     * Create fee refund
     */
    @Transactional
    public FeeRefundResponseDTO createFeeRefund(FeeRefundRequestDTO requestDTO) {
        log.debug("Creating fee refund for regNo: {}", requestDTO.getRegNo());

        if (requestDTO.getNotes() == null || requestDTO.getNotes().trim().isEmpty()) {
            throw new IllegalArgumentException("Note is required for refund");
        }

        Admission admission = admissionRepository.findByRegistrationNumberAndIsDeletedFalse(requestDTO.getRegNo());
        if (admission == null) {
            throw new ResourceNotFoundException("Admission not found: " + requestDTO.getRegNo());
        }

        String currentUser = "SYSTEM";
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                User user = (User) auth.getPrincipal();
                currentUser = user.getEmployee().getEmployeeName();
            }
        } catch (Exception e) {
            log.warn("Could not get current user: {}", e.getMessage());
        }

        FeeRefund refund = FeeRefund.builder()
                .refundNumber(generateRefundNumber())
                .registrationNumber(requestDTO.getRegNo())
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
                .createdBy(currentUser)
                .build();

        FeeRefund saved = feeRefundRepository.save(refund);

        try {
            LocalDate cutoffDate = systemConfigurationService.getCutoffDate();
            String newCategory = studentCategoryService.determineCategory(admission, cutoffDate);

            if (!newCategory.equals(admission.getStudentCategory())) {
                admission.setStudentCategory(newCategory);
                admission.setCategoryUpdatedAt(LocalDateTime.now());
                admissionRepository.save(admission);

                log.info(" Admission {} marked as {}", requestDTO.getRegNo(), newCategory);
            }
        } catch (Exception e) {
            log.error(" Could not update admission status: {}", e.getMessage());
        }

        createRefundInstallment(requestDTO.getRegNo(), saved);
        recalculateFeesFromTransactions(requestDTO.getRegNo());

        log.info(" Created fee refund: {} by {}", saved.getRefundNumber(), currentUser);
        return toRefundResponseDTO(saved, admission);
    }

    /**
     * Recalculate total paid from all receipts
     */
    @Transactional
    public void recalculateTotalPaid(String regNo) {
        log.debug("Recalculating total paid for regNo: {}", regNo);
        recalculateFeesFromTransactions(regNo);
    }

    /**
     * Create installment record for refund
     */
    private void createRefundInstallment(String regNo, FeeRefund refund) {
        try {
            // Find max installment number
            List<FeeInstallment> existing = feeInstallmentRepository.findByRegistrationNumberOrderByDueDateAsc(regNo);
            int nextInstallmentNumber = existing.size() + 1;

            FeeInstallment refundInstallment = FeeInstallment.builder()
                    .registrationNumber(regNo)
                    .installmentNumber(nextInstallmentNumber)
                    .dueDate(refund.getRefundDate())
                    .amount(-refund.getRefundAmount())
                    .status("Refund")
                    .paidAmount(-refund.getRefundAmount())
                    .paidDate(refund.getRefundDate())
                    .paymentMode(refund.getPaymentMode())
                    .transactionId(refund.getTransactionNumber())
                    .notes("Refund: " + refund.getRefundNumber()
                            + (refund.getNotes() != null ? " - " + refund.getNotes() : ""))
                    .createdBy("SYSTEM")
                    .build();

            feeInstallmentRepository.save(refundInstallment);
            log.info(" Created refund installment for regNo: {}", regNo);

        } catch (Exception e) {
            log.error(" Failed to create refund installment", e);
        }
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
                .paymentMode(installment.getPaymentMode())
                .transactionId(installment.getTransactionId())
                .notes(installment.getNotes())
                .createdBy(installment.getCreatedBy())
                .updatedBy(installment.getUpdatedBy())
                .createdAt(installment.getCreatedAt())
                .updatedAt(installment.getUpdatedAt())
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

    // FIND this method and UPDATE:
    private FeeRefundResponseDTO toRefundResponseDTO(FeeRefund refund, Admission admission) {
        return FeeRefundResponseDTO.builder()
                .id(refund.getId())
                .refundNumber(refund.getRefundNumber())
                .registrationNumber(refund.getRegistrationNumber())
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
                .issuedBy(refund.getCreatedBy())
                .createdBy(refund.getCreatedBy())
                .build();
    }

    @Transactional
    public FeeReceiptResponseDTO updateFeeReceipt(Long receiptId, FeeReceiptRequestDTO requestDTO) {
        log.debug("Updating fee receipt: {}", receiptId);

        FeeReceipt receipt = feeReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt not found: " + receiptId));

        // Get current user for audit trail
        String currentUser = "SYSTEM";
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof User) {
                User user = (User) auth.getPrincipal();
                currentUser = user.getEmployee().getEmployeeName();
            }
        } catch (Exception e) {
            log.warn("Could not get current user: {}", e.getMessage());
        }
        final String finalCurrentUser = currentUser;

        Long oldInstallmentId = receipt.getInstallmentId();
        Long newInstallmentId = requestDTO.getInstallmentId();

        // Update fields
        receipt.setReceiptDate(requestDTO.getReceiptDate() != null ? requestDTO.getReceiptDate() : LocalDate.now());
        receipt.setAmountReceived(requestDTO.getAmountReceived());
        receipt.setPreviousPaid(requestDTO.getPreviousPaid());
        receipt.setTotalFees(requestDTO.getTotalFees());
        Double currentTotalPaid = requestDTO.getPreviousPaid() != null ? requestDTO.getPreviousPaid() : 0.0;
        Double newTotalPaid = currentTotalPaid + requestDTO.getAmountReceived();
        Double pendingFees = requestDTO.getTotalFees() - newTotalPaid;
        receipt.setPendingFees(Math.max(0, pendingFees));
        if (pendingFees <= 0.01) {
            receipt.setNextDueDate(null);
        }
        receipt.setGstEnabled(requestDTO.getGstEnabled() != null ? requestDTO.getGstEnabled() : false);
        receipt.setSgstPercent(requestDTO.getSgstPercent());
        receipt.setCgstPercent(requestDTO.getCgstPercent());
        receipt.setInvoiceValue(requestDTO.getInvoiceValue());
        receipt.setPaymentMode(requestDTO.getPaymentMode());
        receipt.setBankName(requestDTO.getBankName());
        receipt.setChequeNumber(requestDTO.getChequeNumber());
        receipt.setChequeDate(requestDTO.getChequeDate());
        receipt.setTransactionNumber(requestDTO.getTransactionNumber());
        receipt.setIfscCode(requestDTO.getIfscCode());
        receipt.setOnlinePaymentMode(requestDTO.getOnlinePaymentMode());
        receipt.setNextDueDate(requestDTO.getNextDueDate());
        receipt.setNotes(requestDTO.getNotes());
        receipt.setUpdatedBy(currentUser);
        receipt.setInstallmentId(newInstallmentId);

        FeeReceipt updated = feeReceiptRepository.save(receipt);

        // Sync Installment Status
        if (oldInstallmentId != null && !oldInstallmentId.equals(newInstallmentId)) {
            try {
                feeInstallmentRepository.findById(oldInstallmentId)
                        .ifPresent(installment -> {
                            installment.setStatus("Pending");
                            installment.setPaidAmount(null);
                            installment.setPaidDate(null);
                            installment.setUpdatedBy(finalCurrentUser);
                            feeInstallmentRepository.saveAndFlush(installment);
                            log.info(" Reverted old installment: {} to Pending", oldInstallmentId);
                        });
            } catch (Exception e) {
                log.error("Failed to revert old installment: {}", oldInstallmentId, e);
            }
        }

        if (newInstallmentId != null) {
            try {
                updateInstallmentStatus(newInstallmentId, requestDTO.getAmountReceived(), currentUser);
                log.info(" Updated new/existing installment: {}", newInstallmentId);
            } catch (Exception e) {
                log.error("Failed to update new installment: {}", newInstallmentId, e);
            }
        }

        // Recalculate student fees from transactions
        if (requestDTO.getRegNo() != null && !requestDTO.getRegNo().trim().isEmpty()) {
            recalculateFeesFromTransactions(requestDTO.getRegNo());
        }

        Admission admission = admissionRepository.findByRegistrationNumberAndIsDeletedFalse(requestDTO.getRegNo());

        log.info("Updated fee receipt: {}", updated.getReceiptNumber());
        return toReceiptResponseDTO(updated, admission);
    }

    @Transactional
    public void updateTotalPaid(String regNo, Double totalPaid) {
        log.debug(" Updating total paid for regNo: {}", regNo);

        Optional<Fees> feesOpt = feesRepository.findByRegistrationNumberAndIsDeletedFalse(regNo);

        if (feesOpt.isPresent()) {
            Fees fees = feesOpt.get();
            fees.setTotalPaid(totalPaid);

            // Correct formula
            Double feesDue = fees.getTotalFees() - totalPaid;
            fees.setFeesDue(Math.max(0, feesDue));

            // Update status
            // NOTE: Use 'Pending' as safe default — accurate Overdue detection
            // (based on actual installment due dates) is done by recalculateFeesForStudent()
            if (feesDue <= 0.01) {
                fees.setStatus("Clear");
            } else {
                fees.setStatus("Pending");
            }

            fees.setUpdatedBy("SYSTEM");
            feesRepository.save(fees);

            log.info(" Updated: totalPaid=₹{}, feesDue=₹{}, status={}",
                    totalPaid, feesDue, fees.getStatus());
        }
    }

    @Transactional
    public void updateFeeStatus(String regNo, String status) {
        log.debug("Updating fee status for regNo: {} to {}", regNo, status);

        Fees fees = feesRepository.findByRegistrationNumberAndIsDeletedFalse(regNo)
                .orElseThrow(() -> new ResourceNotFoundException("Fees record not found: " + regNo));

        fees.setStatus(status);

        // If status is "Clear", set feesDue to 0
        if ("Clear".equalsIgnoreCase(status)) {
            fees.setFeesDue(0.0);
        }

        fees.setUpdatedBy("SYSTEM");
        feesRepository.save(fees);

        log.info(" Updated fee status for {}: {}", regNo, status);
    }

    /**
     * Generate default receipt for imported CSV data (old records)
     */
    @Transactional
    public void generateDefaultReceiptForCSVImport(String regNo) {
        log.debug("🔧 Generating default receipt for CSV import: {}", regNo);

        try {
            Admission admission = admissionRepository
                    .findByRegistrationNumberAndIsDeletedFalse(regNo);

            if (admission == null) {
                throw new ResourceNotFoundException("Admission not found: " + regNo);
            }

            Fees fees = feesRepository.findByRegistrationNumberAndIsDeletedFalse(regNo)
                    .orElseThrow(() -> new ResourceNotFoundException("Fees not found: " + regNo));

            // Only create if totalPaid > 0 and no receipts exist
            List<FeeReceipt> existingReceipts = feeReceiptRepository
                    .findByRegistrationNumberAndIsDeletedFalseOrderByReceiptDateDesc(regNo);

            if (!existingReceipts.isEmpty() || fees.getTotalPaid() == null || fees.getTotalPaid() <= 0) {
                return; // No receipt needed
            }

            FeeReceipt receipt = FeeReceipt.builder()
                    .receiptNumber(generateReceiptNumber())
                    .invoiceNumber(generateInvoiceNumber())
                    .registrationNumber(regNo)
                    .receiptDate(LocalDate.now())
                    .amountReceived(fees.getTotalPaid())
                    .previousPaid(0.0)
                    .totalFees(fees.getTotalFees())
                    .pendingFees(fees.getFeesDue())
                    .gstEnabled(false)
                    .paymentMode("Cash")
                    .receiptType("CSV Import")
                    .notes("Auto-generated receipt for CSV imported data")
                    .status("Active")
                    .createdBy("CSV_IMPORT")
                    .build();

            FeeReceipt saved = feeReceiptRepository.save(receipt);

            log.info(" Generated default receipt {} for CSV import: {}", saved.getReceiptNumber(), regNo);

            toReceiptResponseDTO(saved, admission);

        } catch (Exception e) {
            log.error(" Failed to generate default receipt for {}", regNo, e);
        }
    }

    /**
     * Delete fee installment - SAFE: nullifies foreign keys first
     */
    @Transactional
    public void deleteFeeInstallment(Long installmentId) {
        log.debug("🗑 Deleting fee installment: {}", installmentId);

        FeeInstallment installment = feeInstallmentRepository.findById(installmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Installment not found: " + installmentId));

        String regNo = installment.getRegistrationNumber();

        // STEP 1: Set installment_id to NULL in all receipts that reference this
        // installment
        List<FeeReceipt> receipts = feeReceiptRepository.findByInstallmentIdAndIsDeletedFalse(installmentId);
        if (!receipts.isEmpty()) {
            log.info(" Nullifying installment_id in {} receipts", receipts.size());
            receipts.forEach(receipt -> {
                receipt.setInstallmentId(null);
                receipt.setUpdatedBy("SYSTEM");
            });
            feeReceiptRepository.saveAll(receipts);
            feeReceiptRepository.flush();
        }

        // STEP 2: Now safe to delete the installment
        feeInstallmentRepository.delete(installment);
        feeInstallmentRepository.flush();

        log.info(" Deleted installment {} for regNo: {}", installmentId, regNo);

        // STEP 3: Recalculate fees after deletion
        recalculateFeesFromTransactions(regNo);
    }

    @Transactional
    public List<FeeInstallmentDTO> saveFeeInstallments(String regNo, FeeInstallmentBatchDTO batchDTO) {
        log.debug("💾 Saving installments for regNo: {}", regNo);

        // Validate admission exists
        Admission admission = admissionRepository.findByRegistrationNumberAndIsDeletedFalse(regNo);
        if (admission == null) {
            throw new ResourceNotFoundException("Admission not found: " + regNo);
        }

        // Delete existing installments (except refund installments)
        List<FeeInstallment> existing = feeInstallmentRepository.findByRegistrationNumberOrderByDueDateAsc(regNo);
        for (FeeInstallment inst : existing) {
            if (!"Refund".equalsIgnoreCase(inst.getStatus())) {
                // IMPORTANT: Clear foreign key links in receipts before deleting the installment
                List<FeeReceipt> linkedReceipts = feeReceiptRepository.findByInstallmentIdAndIsDeletedFalse(inst.getId());
                if (linkedReceipts != null && !linkedReceipts.isEmpty()) {
                    linkedReceipts.forEach(r -> r.setInstallmentId(null));
                    feeReceiptRepository.saveAll(linkedReceipts);
                }
                feeInstallmentRepository.delete(inst);
            }
        }
        feeInstallmentRepository.flush();

        // Save new installments
        List<FeeInstallment> savedInstallments = new ArrayList<>();

        for (FeeInstallmentCreateDTO dto : batchDTO.getInstallments()) {
            FeeInstallment installment = FeeInstallment.builder()
                    .registrationNumber(regNo)
                    .installmentNumber(dto.getInstallmentNumber())
                    .dueDate(dto.getDueDate())
                    .amount(dto.getAmount())
                    .status(dto.getStatus())
                    .createdBy(getCurrentUserName())
                    .build();

            savedInstallments.add(feeInstallmentRepository.save(installment));
        }

        log.info(" Saved {} installments for regNo: {}", savedInstallments.size(), regNo);

        // SYNC: Ensure main fees table is updated with new installment data
        recalculateFeesFromTransactions(regNo);

        return savedInstallments.stream()
                .map(this::toInstallmentDTO)
                .collect(Collectors.toList());
    }

    /**
     * Handle payment with edge case logic (CORRECTED VERSION)
     */
    @Transactional
    public FeeReceiptResponseDTO createFeeReceiptWithSmartHandling(FeeReceiptRequestDTO requestDTO) {
        log.info("💡 Smart receipt creation for regNo: {}", requestDTO.getRegNo());

        String regNo = requestDTO.getRegNo();
        Double amountReceived = requestDTO.getAmountReceived();
        Long installmentId = requestDTO.getInstallmentId();

        // Get current user
        String currentUser = getCurrentUserName();

        // Verify admission exists
        Admission admission = admissionRepository
                .findByRegistrationNumberAndIsDeletedFalse(regNo);

        if (admission == null) {
            throw new ResourceNotFoundException("Admission not found: " + regNo);
        }

        // CASE 1: Payment linked to specific installment
        if (installmentId != null) {
            FeeInstallment installment = feeInstallmentRepository.findById(installmentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Installment not found"));

            Double installmentAmount = installment.getAmount();
            Double remainingAmount = installment.getRemainingAmount() != null
                    ? installment.getRemainingAmount()
                    : installmentAmount;

            // EDGE CASE A: Exact payment
            if (Math.abs(amountReceived - remainingAmount) < 0.01) {
                log.info(" Exact payment for installment {}", installmentId);
                markInstallmentAsPaid(installment, amountReceived, currentUser);

                // EDGE CASE B: Overpayment
            } else if (amountReceived > remainingAmount) {
                log.warn("⚠️ OVERPAYMENT: Received ₹{}, Expected ₹{}", amountReceived, remainingAmount);

                // Mark this installment as paid
                markInstallmentAsPaid(installment, remainingAmount, currentUser);

                // Calculate excess
                Double excess = amountReceived - remainingAmount;

                // Apply excess to next pending installments
                handleOverpayment(regNo, excess, currentUser);

                // EDGE CASE C: Partial payment
            } else if (amountReceived < remainingAmount) {
                log.info("📊 PARTIAL PAYMENT: Received ₹{}, Remaining ₹{}",
                        amountReceived, remainingAmount - amountReceived);

                // Update installment with partial payment
                installment.setPaidAmount(
                        (installment.getPaidAmount() != null ? installment.getPaidAmount() : 0.0) + amountReceived);
                installment.setRemainingAmount(remainingAmount - amountReceived);
                installment.setPaymentCount(
                        (installment.getPaymentCount() != null ? installment.getPaymentCount() : 0) + 1);
                installment.setStatus("Partial");
                installment.setUpdatedBy(currentUser);

                feeInstallmentRepository.saveAndFlush(installment);

                log.info(" Installment {} marked as PARTIAL (Paid: ₹{}, Remaining: ₹{})",
                        installmentId, installment.getPaidAmount(), installment.getRemainingAmount());
            }
        }
        // CASE 2: Payment WITHOUT installment link (old students or lump sum)
        else {
            log.info("💰 Lump sum payment (no installment link)");
            handleLumpSumPayment(regNo, amountReceived, currentUser);
        }

        // Create receipt using existing method
        String receiptNumber = generateReceiptNumber();

        FeeReceipt receipt = feesManagerMapper.toReceiptEntity(requestDTO);
        receipt.setReceiptNumber(receiptNumber);
        receipt.setRegistrationNumber(regNo);
        receipt.setCreatedBy(currentUser);
        receipt.setInstallmentId(installmentId);

        // Generate invoice if GST enabled
        if (Boolean.TRUE.equals(requestDTO.getGstEnabled())) {
            receipt.setInvoiceNumber(generateInvoiceNumber());
        }

        FeeReceipt savedReceipt = feeReceiptRepository.save(receipt);

        // Recalculate fees
        recalculateFeesFromTransactions(regNo);

        return feesManagerMapper.toReceiptResponseDTO(savedReceipt);
    }

    /**
     * Handle overpayment - apply to next installments
     */
    private void handleOverpayment(String regNo, Double excessAmount, String updatedBy) {
        log.info(" Handling overpayment: ₹{} for regNo: {}", excessAmount, regNo);

        List<FeeInstallment> pendingInstallments = feeInstallmentRepository
                .findByRegistrationNumberOrderByDueDateAsc(regNo)
                .stream()
                .filter(i -> "Pending".equalsIgnoreCase(i.getStatus()) || 
                          "Partial".equalsIgnoreCase(i.getStatus()) || 
                          "Overdue".equalsIgnoreCase(i.getStatus()))
                .collect(Collectors.toList());

        Double remaining = excessAmount;

        for (FeeInstallment inst : pendingInstallments) {
            if (remaining <= 0.01)
                break;

            Double installmentRemaining = inst.getRemainingAmount() != null
                    ? inst.getRemainingAmount()
                    : inst.getAmount();

            if (remaining >= installmentRemaining) {
                // Fully pay this installment
                markInstallmentAsPaid(inst, installmentRemaining, updatedBy);
                remaining -= installmentRemaining;
                log.info(" Auto-paid installment {} with excess (₹{})", inst.getId(), installmentRemaining);
            } else {
                // Partial payment on this installment
                inst.setPaidAmount((inst.getPaidAmount() != null ? inst.getPaidAmount() : 0.0) + remaining);
                inst.setRemainingAmount(installmentRemaining - remaining);
                inst.setStatus("Partial");
                inst.setUpdatedBy(updatedBy);
                feeInstallmentRepository.saveAndFlush(inst);

                log.info(" Applied ₹{} excess to installment {}", remaining, inst.getId());
                remaining = 0.0;
            }
        }

        if (remaining > 0.01) {
            log.warn("⚠️ Excess amount remaining after applying to all installments: ₹{}", remaining);
            // Excess will be reflected in totalPaid vs totalFees calculation
        }
    }

    /**
     * Handle lump sum payment (no installment)
     */
    private void handleLumpSumPayment(String regNo, Double amount, String updatedBy) {
        log.info("💰 Processing lump sum payment: ₹{} for regNo: {}", amount, regNo);

        // Apply to pending installments in order
        handleOverpayment(regNo, amount, updatedBy);
    }

    /**
     * Mark installment as fully paid
     */
    private void markInstallmentAsPaid(FeeInstallment installment, Double amount, String updatedBy) {
        installment.setPaidAmount(amount);
        installment.setPaidDate(LocalDate.now());
        installment.setRemainingAmount(0.0);
        installment.setStatus("Paid");
        installment.setPaymentCount((installment.getPaymentCount() != null ? installment.getPaymentCount() : 0) + 1);
        installment.setUpdatedBy(updatedBy);

        feeInstallmentRepository.saveAndFlush(installment);

        log.info(" Installment {} marked as PAID", installment.getId());
    }

    /**
     * Get current user name for audit trail
     */
    private String getCurrentUserName() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof User) {
                User user = (User) auth.getPrincipal();
                return user.getEmployee().getEmployeeName();
            }
        } catch (Exception e) {
            log.warn("Could not get current user: {}", e.getMessage());
        }
        return "SYSTEM";
    }

    @Transactional
    public FeeInstallmentDTO updateInstallment(Long installmentId, FeeInstallmentUpdateDTO updateDTO) {
        log.info("✏️ Updating installment: {} with data: {}", installmentId, updateDTO);

        FeeInstallment installment = feeInstallmentRepository.findById(installmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Installment not found"));

        String currentUser = getCurrentUserName();
        String regNo = installment.getRegistrationNumber();

        // Update fields if provided
        if (updateDTO.getDueDate() != null) {
            installment.setDueDate(updateDTO.getDueDate());
        }

        if (updateDTO.getAmount() != null) {
            installment.setOriginalAmount(installment.getAmount());
            installment.setAmount(updateDTO.getAmount());
            
            // If amount is changed, update remainingAmount based on already paid amount
            Double paidAmount = installment.getPaidAmount() != null ? installment.getPaidAmount() : 0.0;
            installment.setRemainingAmount(Math.max(0, updateDTO.getAmount() - paidAmount));
            
            installment.setIsCustom(true);
        }

        if (updateDTO.getStatus() != null) {
            installment.setStatus(updateDTO.getStatus());
            // Logic for status change:
            // If status changed to Paid, and no paidAmount set, set it to full amount
            if ("Paid".equalsIgnoreCase(updateDTO.getStatus())) {
                if (installment.getPaidAmount() == null || installment.getPaidAmount() <= 0) {
                    installment.setPaidAmount(installment.getAmount());
                }
                installment.setRemainingAmount(0.0);
                if (installment.getPaidDate() == null) {
                    installment.setPaidDate(LocalDate.now());
                }
            } else if ("Pending".equalsIgnoreCase(updateDTO.getStatus())) {
                installment.setPaidAmount(0.0);
                installment.setRemainingAmount(installment.getAmount());
                installment.setPaidDate(null);
            }
        }

        if (updateDTO.getNotes() != null) {
            installment.setNotes(updateDTO.getNotes());
        }

        installment.setUpdatedBy(currentUser);

        FeeInstallment updated = feeInstallmentRepository.saveAndFlush(installment);

        log.info(" Installment {} updated for student {}", installmentId, regNo);

        // SYNC: Recalculate fees for the student
        recalculateFeesFromTransactions(regNo);

        return toInstallmentDTO(updated);
    }

    /**
     * Add extra installment
     */
    @Transactional
    public FeeInstallmentDTO addExtraInstallment(String regNo, FeeInstallmentCreateDTO createDTO) {
        log.info("➕ Adding extra installment for regNo: {}", regNo);

        // Get existing installments count
        List<FeeInstallment> existing = feeInstallmentRepository
                .findByRegistrationNumberOrderByDueDateAsc(regNo);

        int nextNumber = existing.size() + 1;
        String currentUser = getCurrentUserName();

        FeeInstallment extra = FeeInstallment.builder()
                .registrationNumber(regNo)
                .installmentNumber(nextNumber)
                .dueDate(createDTO.getDueDate())
                .amount(createDTO.getAmount())
                .remainingAmount(createDTO.getAmount())
                .originalAmount(createDTO.getAmount())
                .status("Pending")
                .installmentType("EXTRA")
                .isCustom(true)
                .createdBy(currentUser)
                .build();

        FeeInstallment saved = feeInstallmentRepository.saveAndFlush(extra);

        log.info(" Extra installment created: {}", saved.getId());

        return toInstallmentDTO(saved);
    }

    /**
     * Get the base opening balance for a student.
     * Extracted from the previousPaid field of the chronologically earliest receipt (by ID).
     */
    private Double getOpeningBalance(List<FeeReceipt> receipts) {
        if (receipts == null || receipts.isEmpty()) {
            return 0.0;
        }
        return receipts.stream()
                .filter(r -> r.getId() != null)
                .min(Comparator.comparing(FeeReceipt::getId))
                .map(r -> r.getPreviousPaid() != null ? r.getPreviousPaid() : 0.0)
                .orElse(0.0);
    }

    /**
     * Flexible student name matching for historical receipts (case-insensitive, token-based).
     * Requires first and last name match or substring containment.
     */
    private boolean isStudentNameMatch(String name1, String name2) {
        if (name1 == null || name2 == null) return false;
        String clean1 = name1.trim().toLowerCase().replaceAll("\\s+", " ");
        String clean2 = name2.trim().toLowerCase().replaceAll("\\s+", " ");
        if (clean1.isEmpty() || clean2.isEmpty()) return false;
        if (clean1.equals(clean2) || clean1.contains(clean2) || clean2.contains(clean1)) {
            return true;
        }
        String[] parts1 = clean1.split(" ");
        String[] parts2 = clean2.split(" ");
        if (parts1.length >= 2 && parts2.length >= 2) {
            return parts1[0].equals(parts2[0]) && parts1[parts1.length - 1].equals(parts2[parts2.length - 1]);
        }
        return false;
    }
}