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

    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();

        // Total Students
        Long totalStudents = admissionRepository.countTotalAdmissions();
        stats.put("totalStudents", totalStudents != null ? totalStudents : 0L);

        // Total Enquiries
        Long totalEnquiries = enquiryRepository.count();
        stats.put("totalEnquiries", totalEnquiries != null ? totalEnquiries : 0L);

        // Total Fees Collected
        // 1. New students (REG*) - Get totalPaid from Fees table
        Double newStudentFees = feesRepository.findByIsDeletedFalse()
                .stream()
                .filter(f -> f.getRegistrationNumber() != null &&
                        f.getRegistrationNumber().startsWith("REG"))
                .mapToDouble(f -> f.getTotalPaid() != null ? f.getTotalPaid() : 0.0)
                .sum();

        // 2. Old students (CSV import) - Get paidFees from FeeCollection table
        Double oldStudentFees = feeCollectionRepository.findByIsDeletedFalse(null)
                .stream()
                .mapToDouble(c -> c.getPaidFees() != null ? c.getPaidFees() : 0.0)
                .sum();

        Double totalFeesCollected = newStudentFees + oldStudentFees;

        stats.put("totalFeesCollected", totalFeesCollected);
        stats.put("totalFeesCollectedFormatted", formatCurrency(totalFeesCollected));

        log.info(" Total Fees: New Students (REG*): ₹{}, Old Students: ₹{}, Total: ₹{}",
                newStudentFees, oldStudentFees, totalFeesCollected);

        // Certificates Issued
        Long certificatesIssued = certificateRepository.countByStatus("ISSUED");
        stats.put("certificatesIssued", certificatesIssued != null ? certificatesIssued : 0L);

        return stats;
    }

    /**
     * Revenue chart - Include both new and old student fees
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

        //  Get receipts for NEW students (REG*) only
        List<FeeReceipt> newStudentReceipts = feeReceiptRepository.findByDateRange(startDate, endDate)
                .stream()
                .filter(r -> r.getRegistrationNumber() != null &&
                        r.getRegistrationNumber().startsWith("REG"))
                .collect(Collectors.toList());

        //  Get all old student collections (imported data)
        List<FeeCollection> oldStudentCollections = feeCollectionRepository.findByFiltersAsList(
                startDate, endDate, null, null
        );

        List<String> labels = new ArrayList<>();
        List<Double> data = new ArrayList<>();

        if (groupByMonth) {
            // Group by month
            Map<YearMonth, Double> monthlyRevenue = new TreeMap<>();

            // Add NEW student receipts
            for (FeeReceipt receipt : newStudentReceipts) {
                if (receipt.getReceiptDate() != null && receipt.getAmountReceived() != null) {
                    YearMonth yearMonth = YearMonth.from(receipt.getReceiptDate());
                    monthlyRevenue.merge(yearMonth, receipt.getAmountReceived(), Double::sum);
                }
            }

            // Add OLD student collections
            for (FeeCollection collection : oldStudentCollections) {
                if (collection.getReceiptDate() != null && collection.getPaidFees() != null) {
                    YearMonth yearMonth = YearMonth.from(collection.getReceiptDate());
                    monthlyRevenue.merge(yearMonth, collection.getPaidFees(), Double::sum);
                }
            }

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM yyyy");
            for (Map.Entry<YearMonth, Double> entry : monthlyRevenue.entrySet()) {
                labels.add(entry.getKey().format(formatter));
                data.add(entry.getValue());
            }
        } else {
            // Group by day
            Map<LocalDate, Double> dailyRevenue = new TreeMap<>();

            // Add NEW student receipts
            for (FeeReceipt receipt : newStudentReceipts) {
                if (receipt.getReceiptDate() != null && receipt.getAmountReceived() != null) {
                    dailyRevenue.merge(receipt.getReceiptDate(), receipt.getAmountReceived(), Double::sum);
                }
            }

            // Add OLD student collections
            for (FeeCollection collection : oldStudentCollections) {
                if (collection.getReceiptDate() != null && collection.getPaidFees() != null) {
                    dailyRevenue.merge(collection.getReceiptDate(), collection.getPaidFees(), Double::sum);
                }
            }

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM");
            for (Map.Entry<LocalDate, Double> entry : dailyRevenue.entrySet()) {
                labels.add(entry.getKey().format(formatter));
                data.add(entry.getValue());
            }
        }

        chartData.put("labels", labels);
        chartData.put("data", data);
        chartData.put("period", period);

        double totalRevenue = data.stream().mapToDouble(Double::doubleValue).sum();
        log.info(" Revenue chart for {}: {} data points, Total: ₹{} (New: {}, Old: {})",
                period, data.size(), totalRevenue,
                newStudentReceipts.size(), oldStudentCollections.size());

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

        log.info("Course distribution: {} courses found", sortedCourses.size());

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