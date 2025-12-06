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

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.Year;
import java.util.*;
import java.util.stream.Collectors;

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


    @Transactional(readOnly = true)
    public void sendReceiptEmail(String receiptNo, String email, String studentName, String message, String pdfBase64) {
        log.debug("📧 Sending receipt email with PDF for: {}", receiptNo);

        FeeReceipt receipt = feeReceiptRepository.findByReceiptNumberAndIsDeletedFalse(receiptNo)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt not found: " + receiptNo));

        Admission admission = admissionRepository.findByRegistrationNumberAndIsDeletedFalse(receipt.getRegistrationNumber());

        if (admission == null) {
            throw new ResourceNotFoundException("Admission not found for receipt: " + receiptNo);
        }

        // Send email with PDF generated from frontend
        emailTemplateService.sendFeeReceiptWithPDF(
                email,
                studentName,
                receiptNo,
                receipt.getAmountReceived(),
                receipt.getReceiptDate(),
                receipt.getPaymentMode(),
                message,
                pdfBase64
        );

        log.info(" Receipt email sent successfully to: {}", email);
    }

    /**
     *  Generate PDF using iText
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

            document.add(new com.itextpdf.text.Paragraph(" ")); // Spacer

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
            log.error("❌ Failed to generate PDF", e);
            return null;
        }
    }

    /**
     *  Generate receipt as PNG image (fallback)
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
            log.error("❌ Failed to generate image", e);
            return null;
        }
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

                if (fees.getTotalPaid() != null && fees.getTotalPaid() > 0) {
                    try {
                        generateDefaultReceiptForCSVImport(fees.getRegistrationNumber());
                    } catch (Exception e) {
                        log.warn("⚠️ Could not generate default receipt for {}", fees.getRegistrationNumber());
                    }
                }

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

    /**
     *  FIXED: Recalculate fees from installments and refunds
     */
    @Transactional
    public void recalculateFeesFromTransactions(String regNo) {
        log.debug("Recalculating fees from all transactions for regNo: {}", regNo);

        try {
            // Get all receipts
            List<FeeReceipt> receipts = feeReceiptRepository
                    .findByRegistrationNumberAndIsDeletedFalseOrderByReceiptDateDesc(regNo);

            // Get all refunds
            List<FeeRefund> refunds = feeRefundRepository
                    .findByRegistrationNumberAndIsDeletedFalseOrderByRefundDateDesc(regNo);

            // Calculate totals
            Double totalPaid = receipts.stream()
                    .mapToDouble(r -> r.getAmountReceived() != null ? r.getAmountReceived() : 0.0)
                    .sum();

            Double totalRefund = refunds.stream()
                    .mapToDouble(r -> r.getRefundAmount() != null ? r.getRefundAmount() : 0.0)
                    .sum();

            // Update fees record
            feesRepository.findByRegistrationNumberAndIsDeletedFalse(regNo)
                    .ifPresent(fees -> {
                        //  CORRECT CALCULATION: Total Paid - Refund
                        Double netTotalPaid = totalPaid - totalRefund;
                        fees.setTotalPaid(Math.max(0, netTotalPaid));
                        fees.setFeesRefund(totalRefund);

                        //  CORRECT: Fees Due = Total Fees - Net Total Paid
                        Double feesDue = fees.getTotalFees() - netTotalPaid;
                        fees.setFeesDue(Math.max(0, feesDue));

                        //  Auto-update status
                        if (totalRefund > 0) {
                            fees.setStatus("Refund");
                        } else if (feesDue <= 0.01) {
                            fees.setStatus("Clear");
                        } else if (fees.getDueDate() != null && fees.getDueDate().isBefore(LocalDate.now())) {
                            fees.setStatus("Overdue");
                        } else {
                            fees.setStatus("Pending");
                        }

                        fees.setUpdatedBy("SYSTEM");
                        feesRepository.save(fees);

                        log.info(" Recalculated fees for {}: TotalPaid=₹{}, Refund=₹{}, NetPaid=₹{}, Due=₹{}, Status={}",
                                regNo, totalPaid, totalRefund, netTotalPaid, feesDue, fees.getStatus());
                    });

        } catch (Exception e) {
            log.error("❌ Failed to recalculate fees for {}", regNo, e);
        }
    }

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
                .registrationNumber(requestDTO.getRegNo())
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

        recalculateFeesFromTransactions(requestDTO.getRegNo());

        //  Update installment if linked
        if (requestDTO.getInstallmentId() != null) {
            feeInstallmentRepository.findById(requestDTO.getInstallmentId()).ifPresent(installment -> {
                installment.setStatus("Paid");
                installment.setPaidAmount(requestDTO.getAmountReceived());
                installment.setPaidDate(LocalDate.now());
                feeInstallmentRepository.save(installment);
            });
        }

        // Recalculate total paid from all receipts
        recalculateTotalPaid(requestDTO.getRegNo());

        log.info(" Created fee receipt: {}", saved.getReceiptNumber());
        return toReceiptResponseDTO(saved, admission);
    }

    @Transactional(readOnly = true)
    public List<FeeReceiptResponseDTO> getReceiptsByRegNo(String regNo) {
        log.debug("📋 Fetching receipts for regNo: {}", regNo);

        // 1️⃣ FIRST: Try to get from fee_receipts table (NEW entries)
        List<FeeReceipt> receipts = feeReceiptRepository
                .findByRegistrationNumberAndIsDeletedFalseOrderByReceiptDateDesc(regNo);

        if (!receipts.isEmpty()) {
            log.info("✅ Found {} receipts in fee_receipts table", receipts.size());
            return receipts.stream()
                    .map(this::toReceiptDTO)
                    .collect(Collectors.toList());
        }

        // 2️⃣ SECOND: If no receipts found, try fee_collections table (OLD data)
        log.info("🔄 No receipts in fee_receipts, checking fee_collections...");
        List<FeeReceiptResponseDTO> oldReceipts = getReceiptsFromFeeCollections(regNo);

        if (!oldReceipts.isEmpty()) {
            log.info("✅ Found {} records in fee_collections", oldReceipts.size());
            return oldReceipts;
        }

        // 3️⃣ THIRD: If still no data, return empty list
        log.warn("⚠️ No receipts found in either table for regNo: {}", regNo);
        return new ArrayList<>();
    }

    /**
     * Convert FeeReceipt entity to FeeReceiptResponseDTO
     */
    private FeeReceiptResponseDTO toReceiptDTO(FeeReceipt receipt) {
        // Get student name from admission
        String studentName = "N/A";
        String mobile = "N/A";

        try {
            Admission admission = admissionRepository
                    .findByRegistrationNumberAndIsDeletedFalse(receipt.getRegistrationNumber());
            if (admission != null) {
                studentName = admission.getFullName();
                mobile = admission.getMobilePrimary();
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

    /**
     * Get receipts from fee_collections table (old imported data)
     * Converts FeeCollection records to FeeReceiptResponseDTO for display
     */
    @Transactional(readOnly = true)
    public List<FeeReceiptResponseDTO> getReceiptsFromFeeCollections(String regNo) {
        log.debug("🔍 Fetching receipts from fee_collections for regNo: {}", regNo);

        try {
            // Get student from admission
            Admission admission = admissionRepository
                    .findByRegistrationNumberAndIsDeletedFalse(regNo);

            if (admission == null) {
                log.warn("⚠️ No admission found for regNo: {}", regNo);
                return new ArrayList<>();
            }

            String studentName = admission.getFullName();
            String mobile = admission.getMobilePrimary();

            // Search fee_collections by matching student name and mobile
            List<FeeCollection> feeCollections = feeCollectionRepository
                    .findByStudentNameAndMobileAndIsDeletedFalse(studentName, mobile);

            log.info("✅ Found {} records in fee_collections", feeCollections.size());

            // Convert to receipt format
            return feeCollections.stream()
                    .map(fc -> FeeReceiptResponseDTO.builder()
                            .id(fc.getId())
                            .receiptNumber(fc.getReceiptNo() != null ? fc.getReceiptNo() : "N/A")
                            .invoiceNumber("INV-OLD-" + fc.getId())
                            .registrationNumber(regNo)
                            .studentName(studentName)
                            .mobile(mobile)
                            .amountReceived(fc.getPaidFees() != null ? fc.getPaidFees() : 0.0)
                            .receiptDate(fc.getReceiptDate())
                            .paymentMode(fc.getPaymentMode() != null ? fc.getPaymentMode() : "Cash")
                            .notes(fc.getNotes())
                            .receiptType("Old Imported")
                            .status("Completed")
                            .build())
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("❌ Error fetching from fee_collections: {}", e.getMessage());
            return new ArrayList<>();
        }
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
                .createdBy("SYSTEM")
                .build();

        FeeRefund saved = feeRefundRepository.save(refund);
        createRefundInstallment(requestDTO.getRegNo(), saved);
        recalculateFeesFromTransactions(requestDTO.getRegNo());


        //  Update Fees table: reduce totalPaid and update feesDue
        feesRepository.findByRegistrationNumberAndIsDeletedFalse(requestDTO.getRegNo())
                .ifPresent(fees -> {
                    fees.setFeesRefund(requestDTO.getRefundAmount());

                    //  Reduce total paid by refund amount
                    Double newTotalPaid = fees.getTotalPaid() - requestDTO.getRefundAmount();
                    fees.setTotalPaid(Math.max(0, newTotalPaid));

                    // Recalculate: feesDue = totalFees - totalPaid
                    Double newFeesDue = fees.getTotalFees() - fees.getTotalPaid();
                    fees.setFeesDue(Math.max(0, newFeesDue));

                    //  Update status to "Refund"
                    fees.setStatus("Refund");

                    fees.setUpdatedBy("SYSTEM");
                    feesRepository.save(fees);

                    log.info(" Updated fees after refund for regNo: {} - New Total Paid: ₹{}, Status: Refund",
                            requestDTO.getRegNo(), fees.getTotalPaid());
                });

        //  Create installment record for refund
        createRefundInstallment(requestDTO.getRegNo(), saved);

        log.info(" Created fee refund: {}", saved.getRefundNumber());
        return toRefundResponseDTO(saved, admission);
    }

    /**
     *  Recalculate total paid from all receipts
     */
    @Transactional
    public void recalculateTotalPaid(String regNo) {
        log.debug("Recalculating total paid for regNo: {}", regNo);

        try {
            // Get all receipts for this student
            List<FeeReceipt> receipts = feeReceiptRepository
                    .findByRegistrationNumberAndIsDeletedFalseOrderByReceiptDateDesc(regNo);

            // Calculate total from receipts
            Double totalPaid = receipts.stream()
                    .mapToDouble(r -> r.getAmountReceived() != null ? r.getAmountReceived() : 0.0)
                    .sum();

            // Update fees record
            feesRepository.findByRegistrationNumberAndIsDeletedFalse(regNo)
                    .ifPresent(fees -> {
                        fees.setTotalPaid(totalPaid);

                        // Recalculate fees due
                        Double feesDue = fees.getTotalFees() - totalPaid +
                                (fees.getFeesRefund() != null ? fees.getFeesRefund() : 0.0);
                        fees.setFeesDue(Math.max(0, feesDue));

                        //  Auto-update status
                        if (fees.getFeesRefund() != null && fees.getFeesRefund() > 0) {
                            fees.setStatus("Refund");
                        } else if (feesDue <= 0.01) {
                            fees.setStatus("Clear");
                        } else if (fees.getDueDate() != null && fees.getDueDate().isBefore(LocalDate.now())) {
                            fees.setStatus("Overdue");
                        } else {
                            fees.setStatus("Pending");
                        }

                        fees.setUpdatedBy("SYSTEM");
                        feesRepository.save(fees);

                        log.info(" Recalculated total paid for {}: ₹{}, Status: {}",
                                regNo, totalPaid, fees.getStatus());
                    });

        } catch (Exception e) {
            log.error("❌ Failed to recalculate total paid for {}", regNo, e);
        }
    }

    /**
     *  Create installment record for refund
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
                    .notes("Refund: " + refund.getRefundNumber() + (refund.getNotes() != null ? " - " + refund.getNotes() : ""))
                    .createdBy("SYSTEM")
                    .build();

            feeInstallmentRepository.save(refundInstallment);
            log.info(" Created refund installment for regNo: {}", regNo);

        } catch (Exception e) {
            log.error("❌ Failed to create refund installment", e);
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

    @Transactional
    public FeeReceiptResponseDTO updateFeeReceipt(Long receiptId, FeeReceiptRequestDTO requestDTO) {
        log.debug("Updating fee receipt: {}", receiptId);

        FeeReceipt receipt = feeReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt not found: " + receiptId));

        // Update fields
        receipt.setReceiptDate(requestDTO.getReceiptDate() != null ? requestDTO.getReceiptDate() : LocalDate.now());
        receipt.setAmountReceived(requestDTO.getAmountReceived());
        receipt.setPreviousPaid(requestDTO.getPreviousPaid());
        receipt.setTotalFees(requestDTO.getTotalFees());
        receipt.setPendingFees(requestDTO.getPendingFees());
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
        receipt.setUpdatedBy("SYSTEM");

        FeeReceipt updated = feeReceiptRepository.save(receipt);

        Admission admission = admissionRepository.findByRegistrationNumberAndIsDeletedFalse(requestDTO.getRegNo());

        log.info("Updated fee receipt: {}", updated.getReceiptNumber());
        return toReceiptResponseDTO(updated, admission);
    }
    @Transactional
    public void updateTotalPaid(String regNo, Double totalPaid) {
        log.debug("Updating total paid for regNo: {}", regNo);

        Optional<Fees> feesOpt = feesRepository.findByRegistrationNumberAndIsDeletedFalse(regNo);

        if (feesOpt.isPresent()) {
            Fees fees = feesOpt.get();
            fees.setTotalPaid(totalPaid);

            // Recalculate fees due
            Double feesDue = fees.getTotalFees() - totalPaid + (fees.getFeesRefund() != null ? fees.getFeesRefund() : 0.0);
            fees.setFeesDue(Math.max(0, feesDue));

            // Update status
            if (feesDue <= 0.01) { // Allow small rounding errors
                fees.setStatus("Clear");
            } else {
                fees.setStatus("Pending");
            }

            fees.setUpdatedBy("SYSTEM");
            feesRepository.save(fees);
            log.info(" Updated total paid for {}: ₹{}, Status: {}", regNo, totalPaid, fees.getStatus());
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
            log.error("❌ Failed to generate default receipt for {}", regNo, e);
        }
    }
}