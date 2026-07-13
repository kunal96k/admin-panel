package com.tts.sms.service;

import com.tts.sms.model.Admission;
import com.tts.sms.model.FeeCollection;
import com.tts.sms.model.FeeReceipt;
import com.tts.sms.model.FeeRefund;
import com.tts.sms.model.Fees;
import com.tts.sms.repository.AdmissionRepository;
import com.tts.sms.repository.FeeCollectionRepository;
import com.tts.sms.repository.FeeInstallmentRepository;
import com.tts.sms.repository.FeeReceiptRepository;
import com.tts.sms.repository.FeeRefundRepository;
import com.tts.sms.repository.FeesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class FeesManagerServiceTest {

    @Mock
    private AdmissionRepository admissionRepository;
    @Mock
    private FeesRepository feesRepository;
    @Mock
    private FeeReceiptRepository feeReceiptRepository;
    @Mock
    private FeeRefundRepository feeRefundRepository;
    @Mock
    private FeeCollectionRepository feeCollectionRepository;
    @Mock
    private FeeInstallmentRepository feeInstallmentRepository;

    @InjectMocks
    private FeesManagerService feesManagerService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testRecalculateFeesFromTransactions_NewStudent() {
        String regNo = "REG1001";
        
        List<FeeReceipt> receipts = new ArrayList<>();
        FeeReceipt receipt = new FeeReceipt();
        receipt.setAmountReceived(8000.0);
        receipts.add(receipt);
        
        when(feeReceiptRepository.findByRegistrationNumberAndIsDeletedFalseOrderByReceiptDateDesc(regNo))
                .thenReturn(receipts);
        when(feeRefundRepository.findByRegistrationNumberAndIsDeletedFalseOrderByRefundDateDesc(regNo))
                .thenReturn(Collections.emptyList());

        Fees fees = new Fees();
        fees.setRegistrationNumber(regNo);
        fees.setTotalFees(35000.0);
        
        when(feesRepository.findByRegistrationNumberAndIsDeletedFalse(regNo))
                .thenReturn(Optional.of(fees));

        feesManagerService.recalculateFeesFromTransactions(regNo);

        assertEquals(8000.0, fees.getTotalPaid());
        assertEquals(27000.0, fees.getFeesDue());
        verifyNoInteractions(feeCollectionRepository);
    }

    @Test
    void testRecalculateFeesFromTransactions_OldStudent() {
        String regNo = "7042"; // does not start with REG
        
        List<FeeReceipt> receipts = new ArrayList<>();
        FeeReceipt receipt = new FeeReceipt();
        receipt.setAmountReceived(8000.0);
        receipts.add(receipt);
        
        when(feeReceiptRepository.findByRegistrationNumberAndIsDeletedFalseOrderByReceiptDateDesc(regNo))
                .thenReturn(receipts);
        when(feeRefundRepository.findByRegistrationNumberAndIsDeletedFalseOrderByRefundDateDesc(regNo))
                .thenReturn(Collections.emptyList());

        Fees fees = new Fees();
        fees.setRegistrationNumber(regNo);
        fees.setMobile("7620058049");
        fees.setTotalFees(35000.0);
        
        when(feesRepository.findByRegistrationNumberAndIsDeletedFalse(regNo))
                .thenReturn(Optional.of(fees));

        List<FeeCollection> oldCollections = new ArrayList<>();
        FeeCollection fc1 = new FeeCollection();
        fc1.setPaidFees(12500.0);
        oldCollections.add(fc1);
        FeeCollection fc2 = new FeeCollection();
        fc2.setPaidFees(12500.0);
        oldCollections.add(fc2);

        when(feeCollectionRepository.findByMobileNoAndIsDeletedFalse("7620058049"))
                .thenReturn(oldCollections);

        feesManagerService.recalculateFeesFromTransactions(regNo);

        // grossTotalPaid = 8000 + 12500 + 12500 = 33000
        assertEquals(33000.0, fees.getTotalPaid());
        assertEquals(2000.0, fees.getFeesDue());
    }
}
