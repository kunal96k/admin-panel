package com.tts.sms.service;

import com.tts.sms.dto.*;
import com.tts.sms.exception.ResourceNotFoundException;
import com.tts.sms.model.*;
import com.tts.sms.repository.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    /**
     *  Send receipt email - Delegates to async email service
     */
    @Transactional(readOnly = true)
    public void sendReceiptEmail(String receiptNo, String email, String studentName, String message, String pdfBase64) {
        log.debug("📧 Preparing to send receipt email for: {}", receiptNo);

        try {
            FeeReceipt receipt = feeReceiptRepository.findByReceiptNumberAndIsDeletedFalse(receiptNo)
                    .orElseThrow(() -> new ResourceNotFoundException("Receipt not found: " + receiptNo));

            Admission admission = admissionRepository.findByRegistrationNumberAndIsDeletedFalse(receipt.getRegistrationNumber());

            if (admission == null) {
                throw new ResourceNotFoundException("Admission not found for receipt: " + receiptNo);
            }

            //  Call async email service (fire-and-forget)
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

            log.info(" Receipt email queued for sending to: {}", email);

        } catch (ResourceNotFoundException e) {
            log.error(" Resource not found: {}", e.getMessage());
            throw e;  // Re-throw to controller
        } catch (Exception e) {
            log.error(" Failed to queue receipt email for {}: {}", receiptNo, e.getMessage());
            throw new RuntimeException("Failed to send email: " + e.getMessage());
        }
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
            log.error(" Failed to generate image", e);
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
        log.info("🔄 Processing {} fees records", dtos.size());

        int successCount = 0;
        int updateCount = 0;
        int createCount = 0;
        List<FeesBulkImportResponseDTO.ImportError> errors = new ArrayList<>();

        for (int i = 0; i < dtos.size(); i++) {
            final int rowNumber = i + 2;
            FeesCSVImportDTO dto = dtos.get(i);

            try {
                //  VALIDATE REQUIRED FIELDS
                if (dto.getRegistrationNumber() == null || dto.getRegistrationNumber().trim().isEmpty()) {
                    errors.add(FeesBulkImportResponseDTO.ImportError.builder()
                            .rowNumber(rowNumber)
                            .fieldName("registrationNumber")
                            .errorMessage("Registration number is required")
                            .rejectedValue("EMPTY")
                            .build());
                    continue;
                }

                //  VALIDATE STUDENT NAME
                if (dto.getStudentName() == null || dto.getStudentName().trim().isEmpty()) {
                    errors.add(FeesBulkImportResponseDTO.ImportError.builder()
                            .rowNumber(rowNumber)
                            .fieldName("studentName")
                            .errorMessage("Student name is required")
                            .rejectedValue(dto.getRegistrationNumber())
                            .build());
                    continue;
                }

                //  SAVE IN NEW TRANSACTION TO ISOLATE FAILURES
                try {
                    saveFeeRecordInNewTransaction(dto, rowNumber);
                    successCount++;

                    //  Check if it's an update or create - MOVED BEFORE save to avoid duplicate query
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
     *  Save fees record in NEW transaction to isolate failures
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveFeeRecordInNewTransaction(FeesCSVImportDTO dto, int rowNumber) {
        try {
            //  Check if record exists
            Optional<Fees> existingFees = feesRepository
                    .findByRegistrationNumberAndIsDeletedFalse(dto.getRegistrationNumber());

            Fees fees;
            if (existingFees.isPresent()) {
                //  UPDATE EXISTING RECORD
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
                //  CREATE NEW RECORD
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

                //  Try to link to admission
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

            //  SAVE AND FLUSH IMMEDIATELY
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
     *   Recalculate fees from installments and refunds
     */
    @Transactional
    public void recalculateFeesFromTransactions(String regNo) {
        log.debug("🔄 Recalculating fees from all transactions for regNo: {}", regNo);

        try {
            // Get all receipts
            List<FeeReceipt> receipts = feeReceiptRepository
                    .findByRegistrationNumberAndIsDeletedFalseOrderByReceiptDateDesc(regNo);

            // Get all refunds
            List<FeeRefund> refunds = feeRefundRepository
                    .findByRegistrationNumberAndIsDeletedFalseOrderByRefundDateDesc(regNo);

            // Calculate gross total paid (sum of all receipts)
            Double grossTotalPaid = receipts.stream()
                    .mapToDouble(r -> r.getAmountReceived() != null ? r.getAmountReceived() : 0.0)
                    .sum();

            // Calculate total refunds
            Double totalRefund = refunds.stream()
                    .mapToDouble(r -> r.getRefundAmount() != null ? r.getRefundAmount() : 0.0)
                    .sum();

            log.info("📊 Gross Paid: ₹{}, Total Refund: ₹{}", grossTotalPaid, totalRefund);

            // Update fees record
            feesRepository.findByRegistrationNumberAndIsDeletedFalse(regNo)
                    .ifPresent(fees -> {
                        //  Net amount = Gross Paid - Refunds
                        Double netTotalPaid = grossTotalPaid - totalRefund;

                        //  Store NET paid in database (what student actually paid)
                        fees.setTotalPaid(Math.max(0, netTotalPaid));

                        //  Store refund amount separately
                        fees.setFeesRefund(totalRefund);

                        //  Fees Due = Total Fees - Net Paid
                        Double feesDue = fees.getTotalFees() - netTotalPaid;
                        fees.setFeesDue(Math.max(0, feesDue));

                        //  Auto-update status
                        if (totalRefund > 0 && feesDue > 0.01) {
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

                        log.info(" Updated Fees - RegNo: {}, TotalFees: ₹{}, NetPaid: ₹{}, Due: ₹{}, Refund: ₹{}, Status: {}",
                                regNo, fees.getTotalFees(), netTotalPaid, feesDue, totalRefund, fees.getStatus());
                    });

        } catch (Exception e) {
            log.error(" Failed to recalculate fees for {}", regNo, e);
            throw new RuntimeException("Failed to recalculate fees: " + e.getMessage(), e);
        }
    }

    @Transactional
    public FeeReceiptResponseDTO createFeeReceipt(FeeReceiptRequestDTO requestDTO) {
        log.debug("Creating fee receipt for regNo: {}", requestDTO.getRegNo());

        //  VALIDATION: Only allow receipts for REG* numbers
        if (requestDTO.getRegNo() == null || !requestDTO.getRegNo().startsWith("REG")) {
            log.error(" Cannot create receipt for non-REG admission: {}", requestDTO.getRegNo());
            throw new IllegalArgumentException(
                    "Cannot create fee receipt for non-REG admission. Only admissions with registration numbers starting with 'REG' can have receipts.");
        }

        // Verify admission exists
        Admission admission = admissionRepository
                .findByRegistrationNumberAndIsDeletedFalse(requestDTO.getRegNo());

        if (admission == null) {
            log.error(" Admission not found with registration number: {}", requestDTO.getRegNo());
            throw new ResourceNotFoundException(
                    "Admission not found with registration number: " + requestDTO.getRegNo());
        }

        log.info(" Verified admission exists: {} ({})", admission.getFullName(), requestDTO.getRegNo());

        // Generate receipt number
        String receiptNumber = generateReceiptNumber();
        log.debug("Generated receipt number: {}", receiptNumber);

        // Create receipt entity
        FeeReceipt receipt = feesManagerMapper.toReceiptEntity(requestDTO);
        receipt.setReceiptNumber(receiptNumber);
        receipt.setRegistrationNumber(requestDTO.getRegNo());
        receipt.setCreatedBy("SYSTEM");

        // Generate invoice number if GST is enabled
        if (Boolean.TRUE.equals(requestDTO.getGstEnabled())) {
            String invoiceNumber = generateInvoiceNumber();
            receipt.setInvoiceNumber(invoiceNumber);
            log.debug("Generated invoice number: {}", invoiceNumber);
        }

        //  ALWAYS generate invoice number (not just for GST)
//        String invoiceNumber = generateInvoiceNumber();
//        receipt.setInvoiceNumber(invoiceNumber);
//        log.debug("Generated invoice number: {}", invoiceNumber);

        // Save receipt
        FeeReceipt savedReceipt = feeReceiptRepository.save(receipt);
        log.info(" Created fee receipt: {} for regNo: {}", receiptNumber, requestDTO.getRegNo());

        //  Update Fees table - ONLY for REG* admissions
        try {
            updateFeesTableAfterReceipt(requestDTO.getRegNo(), requestDTO.getAmountReceived());
            log.info(" Updated fees table for regNo: {}", requestDTO.getRegNo());
        } catch (Exception e) {
            log.error(" Failed to update fees table for regNo: {}", requestDTO.getRegNo(), e);
            // Don't throw - receipt is already saved
        }

        // Update installment if provided
        if (requestDTO.getInstallmentId() != null) {
            try {
                updateInstallmentStatus(requestDTO.getInstallmentId(), requestDTO.getAmountReceived());
                log.info(" Updated installment: {}", requestDTO.getInstallmentId());
            } catch (Exception e) {
                log.error(" Failed to update installment: {}", requestDTO.getInstallmentId(), e);
            }
        }

        return feesManagerMapper.toReceiptResponseDTO(savedReceipt);
    }

    /**
     *  Generate receipt number (e.g., REC0001, REC0002)
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
     *  Generate invoice number (e.g., INV0001, INV0002)
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
     *  Update Fees table after receipt creation - ONLY for REG* admissions
     */
    private void updateFeesTableAfterReceipt(String registrationNumber, Double amountReceived) {
        if (!registrationNumber.startsWith("REG")) {
            log.info("⏭️ Skipping fees update for non-REG admission: {}", registrationNumber);
            return;
        }

        feesRepository.findByRegistrationNumberAndIsDeletedFalse(registrationNumber)
                .ifPresent(fees -> {
                    // Update total paid
                    Double currentTotalPaid = fees.getTotalPaid() != null ? fees.getTotalPaid() : 0.0;
                    fees.setTotalPaid(currentTotalPaid + amountReceived);

                    //  Correct calculation
                    Double feesDue = fees.getTotalFees() - fees.getTotalPaid();
                    fees.setFeesDue(Math.max(0, feesDue));

                    // Update status
                    if (feesDue <= 0.01) {
                        fees.setStatus("Clear");
                    } else {
                        fees.setStatus("Pending");
                    }

                    fees.setUpdatedBy("SYSTEM");
                    feesRepository.save(fees);

                    log.info(" Updated fees: totalPaid=₹{}, feesDue=₹{}, status={}",
                            fees.getTotalPaid(), fees.getFeesDue(), fees.getStatus());
                });
    }

    /**
     *  Update installment status after payment
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
    public List<FeeReceiptResponseDTO> getReceiptsByRegNo(String regNo) {
        log.debug("📋 Fetching receipts for regNo: {}", regNo);

        List<FeeReceiptResponseDTO> allReceipts = new ArrayList<>();

        try {
            // 1️⃣ Check if this is an OLD imported student (non-REG numbers)
            boolean isOldStudent = (regNo != null && !regNo.startsWith("REG"));

            if (isOldStudent) {
                log.info("🔍 OLD STUDENT detected ({}), fetching from fee_collections ONLY", regNo);

                try {
                    //  FIRST: Try to get mobile from FEES table (more reliable for old students)
                    Optional<Fees> feesOpt = feesRepository
                            .findByRegistrationNumberAndIsDeletedFalse(regNo);

                    if (feesOpt.isPresent()) {
                        Fees fees = feesOpt.get();
                        String mobile = fees.getMobile();
                        String studentName = fees.getStudentName();

                        log.info(" Found fees record - Name: '{}', Mobile: '{}'", studentName, mobile);

                        if (mobile != null && !mobile.trim().isEmpty() && !"N/A".equals(mobile)) {
                            log.debug(" Searching fee_collections with Mobile: '{}'", mobile);

                            // Search ONLY by mobile number (most reliable)
                            List<FeeCollection> oldCollections = feeCollectionRepository
                                    .findByMobileNoAndIsDeletedFalse(mobile);

                            log.info(" Found {} records in fee_collections for mobile: {}",
                                    oldCollections.size(), mobile);

                            if (!oldCollections.isEmpty()) {
                                // Convert to receipt DTOs
                                List<FeeReceiptResponseDTO> oldReceipts = oldCollections.stream()
                                        .map(fc -> FeeReceiptResponseDTO.builder()
                                                .id(fc.getId())
                                                .receiptNumber(fc.getReceiptNo() != null ? fc.getReceiptNo() : "OLD-" + fc.getId())
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
                                                .dataSource("IMPORTED_OLD_DATA")
                                                .build())
                                        .collect(Collectors.toList());

                                allReceipts.addAll(oldReceipts);
                            } else {
                                log.warn(" No fee_collections records found for mobile: {}", mobile);
                            }
                        } else {
                            log.warn(" Mobile number is null/empty/N/A for regNo: {}", regNo);
                        }
                    } else {
                        log.warn(" No fees record found for OLD regNo: {}", regNo);

                        // 🔄 FALLBACK: Try admission table
                        Admission admission = admissionRepository
                                .findByRegistrationNumberAndIsDeletedFalse(regNo);

                        if (admission != null) {
                            String mobile = admission.getMobilePrimary();
                            String studentName = admission.getFullName();

                            log.info("🔄 Fallback: Found admission - Name: '{}', Mobile: '{}'",
                                    studentName, mobile);

                            if (mobile != null && !mobile.trim().isEmpty()) {
                                List<FeeCollection> oldCollections = feeCollectionRepository
                                        .findByMobileNoAndIsDeletedFalse(mobile);

                                log.info(" Fallback: Found {} records in fee_collections",
                                        oldCollections.size());

                                if (!oldCollections.isEmpty()) {
                                    List<FeeReceiptResponseDTO> oldReceipts = oldCollections.stream()
                                            .map(fc -> FeeReceiptResponseDTO.builder()
                                                    .id(fc.getId())
                                                    .receiptNumber(fc.getReceiptNo() != null ? fc.getReceiptNo() : "OLD-" + fc.getId())
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
                                                    .dataSource("IMPORTED_OLD_DATA")
                                                    .build())
                                            .collect(Collectors.toList());

                                    allReceipts.addAll(oldReceipts);
                                }
                            }
                        } else {
                            log.error(" No admission found for OLD regNo: {}", regNo);
                        }
                    }

                } catch (Exception e) {
                    log.error(" Error fetching from fee_collections for {}: {}", regNo, e.getMessage(), e);
                }

            } else {
                // 2️⃣ NEW STUDENT (REG*) - Fetch from fee_receipts table
                log.info("🔍 NEW STUDENT detected ({}), fetching from fee_receipts table", regNo);

                List<FeeReceipt> newReceipts = feeReceiptRepository
                        .findByRegistrationNumberAndIsDeletedFalseOrderByReceiptDateDesc(regNo);

                log.info(" Found {} receipts in fee_receipts table", newReceipts.size());

                if (!newReceipts.isEmpty()) {
                    allReceipts.addAll(newReceipts.stream()
                            .map(this::toReceiptDTO)
                            .collect(Collectors.toList()));
                }
            }

            // 3️⃣ Sort all receipts by date (newest first)
            allReceipts.sort((a, b) -> {
                if (a.getReceiptDate() == null) return 1;
                if (b.getReceiptDate() == null) return -1;
                return b.getReceiptDate().compareTo(a.getReceiptDate());
            });

            log.info("📊 Total receipts returned: {} (RegNo: {}, Type: {})",
                    allReceipts.size(), regNo, isOldStudent ? "OLD" : "NEW");

            return allReceipts;

        } catch (Exception e) {
            log.error(" Error in getReceiptsByRegNo for {}: {}", regNo, e.getMessage(), e);
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

        try {
            // Get student from admission
            Admission admission = admissionRepository
                    .findByRegistrationNumberAndIsDeletedFalse(regNo);

            if (admission == null) {
                log.warn(" No admission found for regNo: {}", regNo);
                return new ArrayList<>();
            }

            String studentName = admission.getFullName();
            String mobile = admission.getMobilePrimary();

            // Search fee_collections by matching student name and mobile
            List<FeeCollection> feeCollections = feeCollectionRepository
                    .findByStudentNameAndMobileAndIsDeletedFalse(studentName, mobile);

            log.info(" Found {} records in fee_collections", feeCollections.size());

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
            log.error(" Error fetching from fee_collections: {}", e.getMessage());
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

        // Validate note field
        if (requestDTO.getNotes() == null || requestDTO.getNotes().trim().isEmpty()) {
            throw new IllegalArgumentException("Note is required for refund");
        }

        Admission admission = admissionRepository.findByRegistrationNumberAndIsDeletedFalse(requestDTO.getRegNo());
        if (admission == null) {
            throw new ResourceNotFoundException("Admission not found: " + requestDTO.getRegNo());
        }

        // Get current user
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

            if (admission != null) {
                LocalDate cutoffDate = systemConfigurationService.getCutoffDate();
                String newCategory = studentCategoryService.determineCategory(admission, cutoffDate);

                if (!newCategory.equals(admission.getStudentCategory())) {
                    admission.setStudentCategory(newCategory);
                    admission.setCategoryUpdatedAt(LocalDateTime.now());
                    admissionRepository.save(admission);

                    log.info(" Admission {} marked as {}", requestDTO.getRegNo(), newCategory);
                }
            }
        } catch (Exception e) {
            log.error("⚠️ Could not update admission status: {}", e.getMessage());
        }

        //  Create refund installment
        createRefundInstallment(requestDTO.getRegNo(), saved);

        //  Recalculate ALL fees from transactions (this uses the correct formula)
        recalculateFeesFromTransactions(requestDTO.getRegNo());

        log.info(" Created fee refund: {} by {}", saved.getRefundNumber(), currentUser);
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
            log.error(" Failed to recalculate total paid for {}", regNo, e);
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
        receipt.setUpdatedBy("SYSTEM");

        FeeReceipt updated = feeReceiptRepository.save(receipt);

        Admission admission = admissionRepository.findByRegistrationNumberAndIsDeletedFalse(requestDTO.getRegNo());

        log.info("Updated fee receipt: {}", updated.getReceiptNumber());
        return toReceiptResponseDTO(updated, admission);
    }

    @Transactional
    public void updateTotalPaid(String regNo, Double totalPaid) {
        log.debug("🔄 Updating total paid for regNo: {}", regNo);

        Optional<Fees> feesOpt = feesRepository.findByRegistrationNumberAndIsDeletedFalse(regNo);

        if (feesOpt.isPresent()) {
            Fees fees = feesOpt.get();
            fees.setTotalPaid(totalPaid);

            //  Correct formula
            Double feesDue = fees.getTotalFees() - totalPaid;
            fees.setFeesDue(Math.max(0, feesDue));

            // Update status
            if (feesDue <= 0.01) {
                fees.setStatus("Clear");
            } else if (fees.getDueDate() != null && fees.getDueDate().isBefore(LocalDate.now())) {
                fees.setStatus("Overdue");
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

        // STEP 1: Set installment_id to NULL in all receipts that reference this installment
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

        //  Delete existing installments (except refund installments)
        List<FeeInstallment> existing = feeInstallmentRepository.findByRegistrationNumberOrderByDueDateAsc(regNo);
        existing.stream()
                .filter(inst -> !"Refund".equalsIgnoreCase(inst.getStatus()))
                .forEach(inst -> feeInstallmentRepository.delete(inst));

        //  Save new installments
        List<FeeInstallment> savedInstallments = new ArrayList<>();

        for (FeeInstallmentCreateDTO dto : batchDTO.getInstallments()) {
            FeeInstallment installment = FeeInstallment.builder()
                    .registrationNumber(regNo)
                    .installmentNumber(dto.getInstallmentNumber())
                    .dueDate(dto.getDueDate())
                    .amount(dto.getAmount())
                    .status(dto.getStatus())
                    .createdBy("SYSTEM")
                    .build();


            savedInstallments.add(feeInstallmentRepository.save(installment));
        }

        log.info(" Saved {} installments for regNo: {}", savedInstallments.size(), regNo);

        return savedInstallments.stream()
                .map(this::toInstallmentDTO)
                .collect(Collectors.toList());
    }
}