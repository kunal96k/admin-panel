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
    private final CertificateRepository certificateRepository;
    private final FeeReceiptRepository feeReceiptRepository;
    private final FeeCollectionRepository feeCollectionRepository;
    private final FeeRefundRepository feeRefundRepository;
    private final SystemConfigurationService systemConfigurationService;

    /**
     * ✅ FIXED: Dashboard stats FROM CUTOFF DATE onwards
     * Total Collected = Total Paid - Total Refunds
     */
    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();

        // Get cutoff date from system config
        LocalDate cutoffDate = systemConfigurationService.getCutoffDate();
        log.info("📊 Dashboard stats calculated from cutoff date: {}", cutoffDate);

        // ========== TOTAL STUDENTS (ALL TIME - NO FILTER) ==========
        Long totalStudents = admissionRepository.countTotalAdmissions();
        stats.put("totalStudents", totalStudents != null ? totalStudents : 0L);
        log.info("👥 Total Students: {}", totalStudents);

        // ========== TOTAL ENQUIRIES (ALL TIME - NO FILTER) ==========
        Long totalEnquiries = enquiryRepository.count();
        stats.put("totalEnquiries", totalEnquiries != null ? totalEnquiries : 0L);
        log.info("📋 Total Enquiries: {}", totalEnquiries);

        // ========== FEES CALCULATIONS (FROM CUTOFF DATE) ==========

        // 1️⃣ Get ALL fees records FROM cutoff date onwards (using created_at)
        List<Fees> feesFromCutoff = feesRepository.findByIsDeletedFalse()
                .stream()
                .filter(f -> f.getCreatedAt() != null &&
                        !f.getCreatedAt().toLocalDate().isBefore(cutoffDate))
                .collect(Collectors.toList());

        log.info("📊 Found {} fees records from cutoff date {}", feesFromCutoff.size(), cutoffDate);

        // 2️⃣ Calculate GROSS total paid (sum of totalPaid from fees table)
        Double grossTotalPaid = feesFromCutoff.stream()
                .mapToDouble(f -> f.getTotalPaid() != null ? f.getTotalPaid() : 0.0)
                .sum();

        // 3️⃣ Get ALL refunds FROM cutoff date onwards (using created_at)
        List<FeeRefund> refundsFromCutoff = feeRefundRepository.findByIsDeletedFalse(null)
                .stream()
                .filter(r -> r.getCreatedAt() != null &&
                        !r.getCreatedAt().toLocalDate().isBefore(cutoffDate))
                .collect(Collectors.toList());

        log.info("📊 Found {} refund records from cutoff date {}", refundsFromCutoff.size(), cutoffDate);

        // 4️⃣ Calculate total refunds
        Double totalRefunds = refundsFromCutoff.stream()
                .mapToDouble(r -> r.getRefundAmount() != null ? r.getRefundAmount() : 0.0)
                .sum();

        // 5️⃣ ✅ CORRECT FORMULA: Net Collected = Gross Paid - Refunds
        Double netCollected = grossTotalPaid - totalRefunds;

        // 6️⃣ Calculate total pending (sum of feesDue)
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
        log.info("💰 FINAL CALCULATIONS:");
        log.info("   - Gross Paid: ₹{} (from {} fees records)", grossTotalPaid, feesFromCutoff.size());
        log.info("   - Total Refunds: ₹{} (from {} refund records)", totalRefunds, refundsFromCutoff.size());
        log.info("   - NET Collected: ₹{}", netCollected);
        log.info("   - Pending Fees: ₹{}", totalPending);
        log.info("   - Cutoff Date: {}", cutoffDate);

        return stats;
    }

    /**
     * ✅ FIXED: Revenue chart - FROM CUTOFF DATE onwards
     * Shows NET revenue (Paid - Refunds)
     */
    public Map<String, Object> getRevenueChartData(String period) {
        Map<String, Object> chartData = new HashMap<>();
        LocalDate endDate = LocalDate.now();
        LocalDate startDate;
        boolean groupByMonth = false;

        // Determine date range
        switch (period.toUpperCase()) {
            case "1M":
                startDate = endDate.minusMonths(1);
                break;
            case "2M":
                startDate = endDate.minusMonths(2);
                break;
            case "3M":
                startDate = endDate.minusMonths(3);
                break;
            case "4M":
                startDate = endDate.minusMonths(4);
                groupByMonth = true;
                break;
            case "5M":
                startDate = endDate.minusMonths(5);
                groupByMonth = true;
                break;
            case "6M":
                startDate = endDate.minusMonths(6);
                groupByMonth = true;
                break;
            case "1Y":
                startDate = endDate.minusYears(1);
                groupByMonth = true;
                break;
            case "2Y":
                startDate = endDate.minusYears(2);
                groupByMonth = true;
                break;
            case "3Y":
                startDate = endDate.minusYears(3);
                groupByMonth = true;
                break;
            case "5Y":
                startDate = endDate.minusYears(5);
                groupByMonth = true;
                break;
            default:
                startDate = endDate.minusMonths(6);
                groupByMonth = true;
        }

        // Get cutoff date
        LocalDate cutoffDate = systemConfigurationService.getCutoffDate();

        // Use the LATER of startDate or cutoffDate
        LocalDate effectiveStartDate = startDate.isBefore(cutoffDate) ? cutoffDate : startDate;

        log.info("📊 Revenue chart: period={}, requestedStart={}, cutoff={}, effectiveStart={}",
                period, startDate, cutoffDate, effectiveStartDate);

        // Get ALL fees records from effective start date
        List<Fees> feesRecords = feesRepository.findByIsDeletedFalse()
                .stream()
                .filter(f -> f.getCreatedAt() != null &&
                        !f.getCreatedAt().toLocalDate().isBefore(effectiveStartDate) &&
                        !f.getCreatedAt().toLocalDate().isAfter(endDate))
                .collect(Collectors.toList());

        // Get ALL refunds from effective start date
        List<FeeRefund> refundRecords = feeRefundRepository.findByIsDeletedFalse(null)
                .stream()
                .filter(r -> r.getCreatedAt() != null &&
                        !r.getCreatedAt().toLocalDate().isBefore(effectiveStartDate) &&
                        !r.getCreatedAt().toLocalDate().isAfter(endDate))
                .collect(Collectors.toList());

        List<String> labels = new ArrayList<>();
        List<Double> data = new ArrayList<>();

        if (groupByMonth) {
            // Group by month
            Map<YearMonth, Double> monthlyPaid = new TreeMap<>();
            Map<YearMonth, Double> monthlyRefunds = new TreeMap<>();

            // Collect payments by month
            for (Fees fee : feesRecords) {
                if (fee.getCreatedAt() != null && fee.getTotalPaid() != null && fee.getTotalPaid() > 0) {
                    YearMonth yearMonth = YearMonth.from(fee.getCreatedAt().toLocalDate());
                    monthlyPaid.merge(yearMonth, fee.getTotalPaid(), Double::sum);
                }
            }

            // Collect refunds by month
            for (FeeRefund refund : refundRecords) {
                if (refund.getCreatedAt() != null && refund.getRefundAmount() != null) {
                    YearMonth yearMonth = YearMonth.from(refund.getCreatedAt().toLocalDate());
                    monthlyRefunds.merge(yearMonth, refund.getRefundAmount(), Double::sum);
                }
            }

            // Calculate NET revenue (Paid - Refunds) for each month
            Set<YearMonth> allMonths = new TreeSet<>(monthlyPaid.keySet());
            allMonths.addAll(monthlyRefunds.keySet());

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM yyyy");
            for (YearMonth month : allMonths) {
                Double paid = monthlyPaid.getOrDefault(month, 0.0);
                Double refunds = monthlyRefunds.getOrDefault(month, 0.0);
                Double netRevenue = paid - refunds;

                labels.add(month.format(formatter));
                data.add(Math.max(0, netRevenue)); // Don't show negative
            }
        } else {
            // Group by day
            Map<LocalDate, Double> dailyPaid = new TreeMap<>();
            Map<LocalDate, Double> dailyRefunds = new TreeMap<>();

            // Collect payments by day
            for (Fees fee : feesRecords) {
                if (fee.getCreatedAt() != null && fee.getTotalPaid() != null && fee.getTotalPaid() > 0) {
                    LocalDate date = fee.getCreatedAt().toLocalDate();
                    dailyPaid.merge(date, fee.getTotalPaid(), Double::sum);
                }
            }

            // Collect refunds by day
            for (FeeRefund refund : refundRecords) {
                if (refund.getCreatedAt() != null && refund.getRefundAmount() != null) {
                    LocalDate date = refund.getCreatedAt().toLocalDate();
                    dailyRefunds.merge(date, refund.getRefundAmount(), Double::sum);
                }
            }

            // Calculate NET revenue (Paid - Refunds) for each day
            Set<LocalDate> allDates = new TreeSet<>(dailyPaid.keySet());
            allDates.addAll(dailyRefunds.keySet());

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM");
            for (LocalDate date : allDates) {
                Double paid = dailyPaid.getOrDefault(date, 0.0);
                Double refunds = dailyRefunds.getOrDefault(date, 0.0);
                Double netRevenue = paid - refunds;

                labels.add(date.format(formatter));
                data.add(Math.max(0, netRevenue)); // Don't show negative
            }
        }

        chartData.put("labels", labels);
        chartData.put("data", data);
        chartData.put("period", period);
        chartData.put("cutoffDate", cutoffDate.toString());

        double totalRevenue = data.stream().mapToDouble(Double::doubleValue).sum();
        log.info("📈 Revenue chart: {} data points, Total NET Revenue: ₹{} (from {})",
                data.size(), totalRevenue, effectiveStartDate);

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

        log.info("📊 Course distribution: {} courses found", sortedCourses.size());

        return chartData;
    }

    /**
     * Generate colors for pie chart
     */
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

    /**
     * Format currency with Indian notation
     */
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