package com.tts.sms.service;

import com.tts.sms.dto.*;
import com.tts.sms.model.*;
import org.springframework.stereotype.Component;

@Component
public class FeesManagerMapper {

    // ==================== FEE RECEIPT MAPPING ====================

    public FeeReceipt toReceiptEntity(FeeReceiptRequestDTO dto) {
        if (dto == null) {
            return null;
        }

        return FeeReceipt.builder()
                .registrationNumber(dto.getRegNo())
                .installmentId(dto.getInstallmentId())
                .receiptDate(dto.getReceiptDate())
                .amountReceived(dto.getAmountReceived())
                .previousPaid(dto.getPreviousPaid())
                .totalFees(dto.getTotalFees())
                .pendingFees(dto.getPendingFees())
                .gstEnabled(dto.getGstEnabled())
                .sgstPercent(dto.getSgstPercent())
                .cgstPercent(dto.getCgstPercent())
                .invoiceValue(dto.getInvoiceValue())
                .paymentMode(dto.getPaymentMode())
                .bankName(dto.getBankName())
                .chequeNumber(dto.getChequeNumber())
                .chequeDate(dto.getChequeDate())
                .transactionNumber(dto.getTransactionNumber())
                .ifscCode(dto.getIfscCode())
                .onlinePaymentMode(dto.getOnlinePaymentMode())
                .nextDueDate(dto.getNextDueDate())
                .receiptType(dto.getReceiptType())
                .notes(dto.getNotes())
                .build();
    }

    public FeeReceiptResponseDTO toReceiptResponseDTO(FeeReceipt receipt) {
        if (receipt == null) {
            return null;
        }

        String studentName = "N/A";
        String regNumber = "N/A";

        if (receipt.getAdmission() != null) {
            studentName = receipt.getAdmission().getFullName();
            regNumber = receipt.getAdmission().getRegistrationNumber();
        }

        return FeeReceiptResponseDTO.builder()
                .id(receipt.getId())
                .receiptNumber(receipt.getReceiptNumber())
                .invoiceNumber(receipt.getInvoiceNumber())
                .studentName(studentName)
                .registrationNumber(regNumber)
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

    // ==================== FEE REFUND MAPPING ====================

    public FeeRefund toRefundEntity(FeeRefundRequestDTO dto) {
        if (dto == null) {
            return null;
        }

        return FeeRefund.builder()
                .registrationNumber(dto.getRegNo())                .refundDate(dto.getRefundDate())
                .refundAmount(dto.getRefundAmount())
                .totalFees(dto.getTotalFees())
                .paidFees(dto.getPaidFees())
                .pendingFees(dto.getPendingFees())
                .paymentMode(dto.getPaymentMode())
                .bankName(dto.getBankName())
                .chequeNumber(dto.getChequeNumber())
                .chequeDate(dto.getChequeDate())
                .transactionNumber(dto.getTransactionNumber())
                .ifscCode(dto.getIfscCode())
                .onlinePaymentMode(dto.getOnlinePaymentMode())
                .paymentClear(dto.getPaymentClear())
                .notes(dto.getNotes())
                .build();
    }

    public FeeRefundResponseDTO toRefundResponseDTO(FeeRefund refund) {
        if (refund == null) {
            return null;
        }

        String studentName = "N/A";
        String regNumber = "N/A";

        if (refund.getAdmission() != null) {
            studentName = refund.getAdmission().getFullName();
            regNumber = refund.getAdmission().getRegistrationNumber();
        }

        return FeeRefundResponseDTO.builder()
                .id(refund.getId())
                .refundNumber(refund.getRefundNumber())
                .studentName(studentName)
                .registrationNumber(regNumber)
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

    // ==================== FEES SUMMARY MAPPING ====================

    public FeesSummaryDTO toFeesSummaryDTO(Admission admission,
                                           Double totalPaid,
                                           Double totalRefund,
                                           Integer totalInstallments,
                                           Integer paidInstallments) {
        if (admission == null) {
            return null;
        }

        Double totalFees = admission.getTotalReceivableFees() != null
                ? admission.getTotalReceivableFees()
                : 0.0;

        totalPaid = totalPaid != null ? totalPaid : 0.0;
        totalRefund = totalRefund != null ? totalRefund : 0.0;

        Double feesDue = totalFees - totalPaid + totalRefund;

        String course = (admission.getCourses() != null && !admission.getCourses().isEmpty())
                ? String.join(", ", admission.getCourses())
                : "N/A";

        String status = feesDue <= 0 ? "Clear" : "Pending";

        return FeesSummaryDTO.builder()
                .admissionId(admission.getId())
                .registrationNumber(admission.getRegistrationNumber())
                .studentName(admission.getFullName())
                .mobile(admission.getMobilePrimary())
                .course(course)
                .totalFees(totalFees)
                .totalPaid(totalPaid)
                .feesDue(feesDue)
                .feesRefund(totalRefund)
                .status(status)
                .totalInstallments(totalInstallments != null ? totalInstallments : 0)
                .paidInstallments(paidInstallments != null ? paidInstallments : 0)
                .pendingInstallments(
                        (totalInstallments != null ? totalInstallments : 0) -
                                (paidInstallments != null ? paidInstallments : 0)
                )
                .build();
    }
}