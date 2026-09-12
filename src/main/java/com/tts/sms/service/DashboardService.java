package com.tts.sms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.tts.sms.repository.*;
import com.tts.sms.model.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final AdmissionRepository admissionRepository;
    private final EnquiryRepository enquiryRepository;
    private final FeesRepository feesRepository;
    private final FeeReceiptRepository feeReceiptRepository;
    private final FeeRefundRepository feeRefundRepository;
    private final FeeCollectionRepository feeCollectionRepository;
    private final SystemConfigurationService systemConfigurationService;

    /**
     *  Dashboard stats based on ADMISSION DATE (not created_at)
     * CORRECT FORMULA:
     * - Total Collected = SUM(totalPaid) - SUM(refunds) [from admissions >= cutoff]
     * - Pending Fees = SUM(feesDue) [from admissions >= cutoff]
     */
    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();

        // Get cutoff date from system config
        LocalDate cutoffDate = systemConfigurationService.getCutoffDate();
        log.info("[STATS] Dashboard stats calculated from cutoff date: {}", cutoffDate);

        // ========== TOTAL STUDENTS (ALL TIME - NO FILTER) ==========
        Long totalStudents = admissionRepository.countTotalAdmissions();
        stats.put("totalStudents", totalStudents != null ? totalStudents : 0L);
        log.info("[USERS] Total Students: {}", totalStudents);

        // ========== TOTAL ENQUIRIES (ALL TIME - NO FILTER) ==========
        Long totalEnquiries = enquiryRepository.count();
        stats.put("totalEnquiries", totalEnquiries != null ? totalEnquiries : 0L);
        log.info("[INFO] Total Enquiries: {}", totalEnquiries);

        // ==========  FEES CALCULATIONS (FROM CUTOFF DATE - BASED ON ADMISSION DATE) ==========

        // Step 1: Get ALL admissions FROM cutoff date onwards (using admission_date)
        List<Admission> admissionsFromCutoff = admissionRepository.findByIsDeletedFalse()
                .stream()
                .filter(a -> a.getAdmissionDate() != null &&
                        !a.getAdmissionDate().isBefore(cutoffDate))
                .collect(Collectors.toList());

        log.info("[STATS] Found {} admissions from cutoff date {} (based on admission_date)",
                admissionsFromCutoff.size(), cutoffDate);

        // Step 2: Get registration numbers for these admissions
        Set<String> regNosFromCutoff = admissionsFromCutoff.stream()
                .map(Admission::getRegistrationNumber)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        log.info("[STATS] Processing fees for {} registration numbers", regNosFromCutoff.size());

        // Step 3: Get ONLY fees records for these admissions
        List<Fees> feesFromCutoff = feesRepository.findByIsDeletedFalse()
                .stream()
                .filter(f -> regNosFromCutoff.contains(f.getRegistrationNumber()))
                .collect(Collectors.toList());

        log.info("[STATS] Found {} fees records for admissions from cutoff date", feesFromCutoff.size());

        // Step 4: Calculate GROSS total paid (sum of totalPaid from fees table)
        Double grossTotalPaid = feesFromCutoff.stream()
                .mapToDouble(f -> f.getTotalPaid() != null ? f.getTotalPaid() : 0.0)
                .sum();

        // Step 5: Get ALL refunds for these registration numbers
        List<FeeRefund> refundsFromCutoff = feeRefundRepository.findByIsDeletedFalse(null)
                .stream()
                .filter(r -> regNosFromCutoff.contains(r.getRegistrationNumber()))
                .collect(Collectors.toList());

        log.info("[STATS] Found {} refund records for admissions from cutoff date",
                refundsFromCutoff.size());

        // Step 6: Calculate total refunds
        Double totalRefunds = refundsFromCutoff.stream()
                .mapToDouble(r -> r.getRefundAmount() != null ? r.getRefundAmount() : 0.0)
                .sum();

        // Step 7: CORRECT FORMULA: Net Collected = Gross Paid - Refunds
        Double netCollected = grossTotalPaid - totalRefunds;

        // Step 8: Calculate total pending (sum of feesDue)
        Double totalPending = feesFromCutoff.stream()
                .mapToDouble(f -> f.getFeesDue() != null ? f.getFeesDue() : 0.0)
                .sum();

        // Store results
        stats.put("totalFeesCollected", Math.max(0, netCollected));
        stats.put("totalFeesCollectedFormatted", formatCurrency(Math.max(0, netCollected)));
        stats.put("cutoffDate", cutoffDate.toString());
        stats.put("cutoffDateFormatted", cutoffDate.format(DateTimeFormatter.ofPattern("dd-MMM-yyyy")));

        stats.put("pendingFees", Math.max(0, totalPending));
        stats.put("pendingFeesFormatted", formatCurrency(Math.max(0, totalPending)));

        // Debug logging
        log.info(" FINAL CALCULATIONS:");
        log.info("   - Admissions from cutoff: {} (based on admission_date >= {})",
                admissionsFromCutoff.size(), cutoffDate);
        log.info("   - Gross Paid: ₹{} (from {} fees records)", grossTotalPaid, feesFromCutoff.size());
        log.info("   - Total Refunds: ₹{} (from {} refund records)", totalRefunds, refundsFromCutoff.size());
        log.info("   - NET Collected: ₹{}", netCollected);
        log.info("   - Pending Fees: ₹{}", totalPending);
        log.info("   - Cutoff Date: {}", cutoffDate);

        return stats;
    }

    /**
     *  FIXED: Revenue chart - Shows data from BOTH old (fee_collections) and new (fee_receipts)
     * Groups by receipt date for proper timeline display
     */
    public Map<String, Object> getRevenueChartData(String period) {
        Map<String, Object> chartData = new HashMap<>();
        LocalDate endDate = LocalDate.now();
        LocalDate startDate;
        boolean groupByMonth = false;

        // Determine date range based on period
        switch (period.toUpperCase()) {
            case "1M": startDate = endDate.minusMonths(1); break;
            case "2M": startDate = endDate.minusMonths(2); break;
            case "3M": startDate = endDate.minusMonths(3); break;
            case "4M": startDate = endDate.minusMonths(4); groupByMonth = true; break;
            case "5M": startDate = endDate.minusMonths(5); groupByMonth = true; break;
            case "6M": startDate = endDate.minusMonths(6); groupByMonth = true; break;
            case "1Y": startDate = endDate.minusYears(1); groupByMonth = true; break;
            case "2Y": startDate = endDate.minusYears(2); groupByMonth = true; break;
            case "3Y": startDate = endDate.minusYears(3); groupByMonth = true; break;
            case "5Y": startDate = endDate.minusYears(5); groupByMonth = true; break;
            default: startDate = endDate.minusMonths(6); groupByMonth = true;
        }

        log.info("[STATS] Revenue chart: period={}, startDate={}, endDate={}", period, startDate, endDate);

        // ==========  STEP 1: Get OLD student receipts from fee_collections ==========
        List<FeeCollection> oldReceipts = feeCollectionRepository.findByFiltersAsList(
                startDate,
                endDate,
                "IMPORTED_OLD_DATA",
                null
        );
        log.info("[STATS] Found {} old receipts from fee_collections", oldReceipts.size());

        // ==========  STEP 2: Get NEW student receipts from fee_receipts (REG*) ==========
        List<FeeReceipt> newReceipts = feeReceiptRepository.findByDateRange(startDate, endDate);
        log.info("[STATS] Found {} new receipts from fee_receipts", newReceipts.size());

        // ==========  STEP 3: Get refunds for the period ==========
        List<FeeRefund> refunds = feeRefundRepository.findByIsDeletedFalse(null).stream()
                .filter(r -> r.getRefundDate() != null &&
                        !r.getRefundDate().isBefore(startDate) &&
                        !r.getRefundDate().isAfter(endDate))
                .collect(Collectors.toList());
        log.info("[STATS] Found {} refunds in date range", refunds.size());

        // ==========  STEP 4: Build chart data ==========
        List<String> labels = new ArrayList<>();
        List<Double> data = new ArrayList<>();

        if (groupByMonth) {
            // ========== GROUP BY MONTH ==========
            Map<YearMonth, Double> monthlyRevenue = new TreeMap<>();

            // Process OLD receipts
            for (FeeCollection fc : oldReceipts) {
                if (fc.getReceiptDate() != null && fc.getPaidFees() != null && fc.getPaidFees() > 0) {
                    YearMonth yearMonth = YearMonth.from(fc.getReceiptDate());
                    monthlyRevenue.merge(yearMonth, fc.getPaidFees(), Double::sum);
                }
            }

            // Process NEW receipts
            for (FeeReceipt receipt : newReceipts) {
                if (receipt.getReceiptDate() != null && receipt.getAmountReceived() != null) {
                    YearMonth yearMonth = YearMonth.from(receipt.getReceiptDate());
                    monthlyRevenue.merge(yearMonth, receipt.getAmountReceived(), Double::sum);
                }
            }

            // Subtract refunds
            for (FeeRefund refund : refunds) {
                if (refund.getRefundDate() != null && refund.getRefundAmount() != null) {
                    YearMonth yearMonth = YearMonth.from(refund.getRefundDate());
                    monthlyRevenue.merge(yearMonth, -refund.getRefundAmount(), Double::sum);
                }
            }

            // Generate all months in range (fill gaps with zero)
            YearMonth startMonth = YearMonth.from(startDate);
            YearMonth endMonth = YearMonth.from(endDate);
            YearMonth current = startMonth;

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM yyyy");

            while (!current.isAfter(endMonth)) {
                Double revenue = monthlyRevenue.getOrDefault(current, 0.0);
                labels.add(current.format(formatter));
                data.add(Math.max(0, revenue)); // Don't show negative values
                current = current.plusMonths(1);
            }

            log.info("[STATS] Generated {} monthly data points", data.size());

        } else {
            // ========== GROUP BY DAY ==========
            Map<LocalDate, Double> dailyRevenue = new TreeMap<>();

            // Process OLD receipts
            for (FeeCollection fc : oldReceipts) {
                if (fc.getReceiptDate() != null && fc.getPaidFees() != null && fc.getPaidFees() > 0) {
                    dailyRevenue.merge(fc.getReceiptDate(), fc.getPaidFees(), Double::sum);
                }
            }

            // Process NEW receipts
            for (FeeReceipt receipt : newReceipts) {
                if (receipt.getReceiptDate() != null && receipt.getAmountReceived() != null) {
                    dailyRevenue.merge(receipt.getReceiptDate(), receipt.getAmountReceived(), Double::sum);
                }
            }

            // Subtract refunds
            for (FeeRefund refund : refunds) {
                if (refund.getRefundDate() != null && refund.getRefundAmount() != null) {
                    dailyRevenue.merge(refund.getRefundDate(), -refund.getRefundAmount(), Double::sum);
                }
            }

            // Generate all dates in range
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM");
            LocalDate current = startDate;

            while (!current.isAfter(endDate)) {
                Double revenue = dailyRevenue.getOrDefault(current, 0.0);
                labels.add(current.format(formatter));
                data.add(Math.max(0, revenue)); // Don't show negative values
                current = current.plusDays(1);
            }

            log.info("[STATS] Generated {} daily data points", data.size());
        }

        chartData.put("labels", labels);
        chartData.put("data", data);
        chartData.put("period", period);
        chartData.put("startDate", startDate.toString());
        chartData.put("endDate", endDate.toString());

        double totalRevenue = data.stream().mapToDouble(Double::doubleValue).sum();
        log.info("[TREND] Revenue chart complete: {} data points, Total NET Revenue: ₹{}",
                data.size(), totalRevenue);

        return chartData;
    }

    /**
     * Get course distribution chart data from Admissions
     */
    public Map<String, Object> getCourseDistributionData() {
        Map<String, Object> chartData = new HashMap<>();

        List<Admission> admissions = admissionRepository.findByIsDeletedFalse();

        Map<String, Long> courseCount = new HashMap<>();

        for (Admission admission : admissions) {
            if (admission.getCourses() != null && !admission.getCourses().isEmpty()) {
                for (String course : admission.getCourses()) {
                    if (course != null && !course.trim().isEmpty() && !"N/A".equalsIgnoreCase(course)) {
                        courseCount.put(course, courseCount.getOrDefault(course, 0L) + 1);
                    }
                }
            }
        }

        List<Map.Entry<String, Long>> sortedCourses = courseCount.entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .collect(Collectors.toList());

        List<String> labels = new ArrayList<>();
        List<Long> data = new ArrayList<>();
        List<String> colors = generateColors(sortedCourses.size());

        for (Map.Entry<String, Long> entry : sortedCourses) {
            labels.add(entry.getKey());
            data.add(entry.getValue());
        }

        chartData.put("labels", labels);
        chartData.put("data", data);
        chartData.put("colors", colors);

        log.info("[STATS] Course distribution: {} courses found", sortedCourses.size());

        return chartData;
    }

    private List<String> generateColors(int count) {
        String[] colorPalette = {
                "#FF6384", "#36A2EB", "#FFCE56", "#4BC0C0", "#9966FF",
                "#FF9F40", "#E7E9ED", "#8E44AD", "#3498DB", "#E74C3C",
                "#2ECC71", "#F39C12", "#1ABC9C", "#D35400", "#C0392B"
        };

        List<String> colors = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            colors.add(colorPalette[i % colorPalette.length]);
        }
        return colors;
    }

    private String formatCurrency(Double amount) {
        if (amount == null || amount == 0) {
            return "₹0";
        }

        if (amount >= 10000000) {
            return String.format("₹%.2fCr", amount / 10000000);
        } else if (amount >= 100000) {
            return String.format("₹%.2fL", amount / 100000);
        } else if (amount >= 1000) {
            return String.format("₹%.2fK", amount / 1000);
        } else {
            return String.format("₹%.2f", amount);
        }
    }
}