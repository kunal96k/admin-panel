package com.tts.sms.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tts.sms.dto.CourseSalesReportDTO;
import com.tts.sms.dto.DataTablesRequest;
import com.tts.sms.dto.DataTablesResponse;
import com.tts.sms.model.Admission;
import com.tts.sms.model.Course;
import com.tts.sms.model.Fees;
import com.tts.sms.repository.AdmissionRepository;
import com.tts.sms.repository.CourseRepository;
import com.tts.sms.repository.FeesRepository;
import com.tts.sms.repository.FeeReceiptRepository;
import com.tts.sms.repository.FeeCollectionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SalesReportService {

    private final AdmissionRepository admissionRepository;
    private final CourseRepository courseRepository;
    private final FeesRepository feesRepository;
    private final FeeReceiptRepository feeReceiptRepository;
    private final FeeCollectionRepository feeCollectionRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MMM-yyyy");

    /**
     * Get course wise sales report with DataTables server-side processing
     */
    /**
     * Get course wise sales report with DataTables server-side processing
     */
    @Transactional(readOnly = true)
    public DataTablesResponse<CourseSalesReportDTO> getCourseSalesReport(
            Long courseId, LocalDate startDate, LocalDate endDate, DataTablesRequest request) {

        log.debug("Generating course sales report - Course: {}, Start: {}, End: {}",
                courseId, startDate, endDate);

        // Validate inputs
        if (courseId == null) throw new IllegalArgumentException("Course ID is required");
        if (startDate == null || endDate == null) throw new IllegalArgumentException("Dates are required");
        if (startDate.isAfter(endDate)) throw new IllegalArgumentException("Start date must be before end date");

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("Course not found: " + courseId));

        // 1. Get admissions list for course and date range (dataset)
        List<Admission> admissions = findAdmissionsList(course.getCourseName(), startDate, endDate);
        int recordsTotalInDataset = admissions.size();

        // 2. Filter by search
        if (request.getSearchValue() != null && !request.getSearchValue().trim().isEmpty()) {
            admissions = filterBySearch(admissions, request.getSearchValue());
        }

        int recordsFiltered = admissions.size();

        // 3. Prepare fees data (needed for sorting by Amount and for Grand Total)
        Map<String, Double> studentFeesMap = new HashMap<>();
        double grandTotal = 0.0;
        for (Admission a : admissions) {
            Double fee = getFeesForAdmission(a);
            studentFeesMap.put(a.getRegistrationNumber(), fee);
            grandTotal += fee;
        }

        // 4. Apply Sort
        sortAdmissions(admissions, studentFeesMap, request);

        // 5. Paginate
        int start = Math.min(request.getStart(), admissions.size());
        int length = request.getLength() > 0 ? request.getLength() : 25;
        int end = Math.min(start + length, admissions.size());
        List<Admission> pagedAdmissions = (start < end) ? admissions.subList(start, end) : new ArrayList<>();

        final double finalGrandTotal = grandTotal;
        List<CourseSalesReportDTO> data = pagedAdmissions.stream()
                .map(a -> convertToDTO(a, studentFeesMap.get(a.getRegistrationNumber()), finalGrandTotal))
                .collect(Collectors.toList());

        DataTablesResponse<CourseSalesReportDTO> response = new DataTablesResponse<>();
        response.setDraw(request.getDraw());
        response.setRecordsTotal(recordsTotalInDataset); 
        response.setRecordsFiltered(recordsFiltered);
        response.setData(data);

        return response;
    }

    private Double getFeesForAdmission(Admission a) {
        if (a == null || a.getRegistrationNumber() == null) return 0.0;
        Double paid = getTotalPaidFromFeesTable(a.getRegistrationNumber());
        return paid != null ? paid : 0.0;
    }

    private void sortAdmissions(List<Admission> admissions, Map<String, Double> feesMap, DataTablesRequest request) {
        int col = request.getOrderColumn();
        boolean asc = "asc".equalsIgnoreCase(request.getOrderDir());

        Comparator<Admission> comparator;
        switch (col) {
            case 0: comparator = Comparator.comparing(a -> a.getRegistrationNumber() != null ? a.getRegistrationNumber() : ""); break;
            case 1: comparator = Comparator.comparing(a -> a.getFullName() != null ? a.getFullName() : ""); break;
            case 2: comparator = Comparator.comparing(a -> a.getMobilePrimary() != null ? a.getMobilePrimary() : ""); break;
            case 3: comparator = Comparator.comparing(a -> a.getAdmissionDate() != null ? a.getAdmissionDate() : LocalDate.MIN); break;
            case 4: comparator = Comparator.comparing(a -> feesMap.getOrDefault(a.getRegistrationNumber(), 0.0)); break;
            default: comparator = Comparator.comparing(a -> a.getAdmissionDate() != null ? a.getAdmissionDate() : LocalDate.MIN); break;
        }

        if (!asc) comparator = comparator.reversed();
        admissions.sort(comparator);
    }

    /**
     * Get total paid fees from Fees, FeeReceipt, or FeeCollection tables by registration number
     */
    private Double getTotalPaidFromFeesTable(String registrationNumber) {
        if (registrationNumber == null || registrationNumber.trim().isEmpty()) {
            return 0.0;
        }

        // 1. Check Fees table totalPaid
        Optional<Fees> feesOpt = feesRepository.findByRegistrationNumberAndIsDeletedFalse(registrationNumber);
        if (feesOpt.isPresent() && feesOpt.get().getTotalPaid() != null && feesOpt.get().getTotalPaid() > 0) {
            return feesOpt.get().getTotalPaid();
        }

        // 2. Fallback: sum of fee_receipts
        Double receiptSum = feeReceiptRepository.sumAmountReceivedByRegistrationNumber(registrationNumber);
        if (receiptSum != null && receiptSum > 0) {
            return receiptSum;
        }

        // 3. Fallback: sum of fee_collections
        Double collectionSum = feeCollectionRepository.sumPaidFeesByRegistrationNumber(registrationNumber);
        if (collectionSum != null && collectionSum > 0) {
            return collectionSum;
        }

        return 0.0;
    }

    /**
     * Get all active courses for dropdown
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAllActiveCourses() {
        List<Course> courses = courseRepository.findByIsActiveTrueOrderByCourseNameAsc();

        return courses.stream()
                .map(course -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("CourseId", course.getId());
                    map.put("CourseName", course.getCourseName());
                    return map;
                })
                .collect(Collectors.toList());
    }

    /**
     * Get sales statistics
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getSalesStatistics(Long courseId, LocalDate startDate, LocalDate endDate) {
        Map<String, Object> stats = new HashMap<>();

        try {
            List<Admission> admissions;

            if (courseId != null && startDate != null && endDate != null) {
                Course course = courseRepository.findById(courseId).orElse(null);
                if (course != null) {
                    admissions = findAdmissionsList(
                            course.getCourseName(), startDate, endDate);
                } else {
                    admissions = new ArrayList<>();
                }
            } else {
                admissions = admissionRepository.findByIsDeletedFalse(PageRequest.of(0, Integer.MAX_VALUE))
                        .getContent();
            }

            // Calculate statistics
            int totalStudents = admissions.size();
            double totalRevenue = calculateTotalAmount(admissions);
            double avgFees = totalStudents > 0 ? totalRevenue / totalStudents : 0.0;

            // Get unique courses count
            Set<String> uniqueCourses = admissions.stream()
                    .flatMap(a -> a.getCourses() != null ? a.getCourses().stream() : Stream.empty())
                    .collect(Collectors.toSet());

            stats.put("totalStudents", totalStudents);
            stats.put("totalRevenue", String.format("%.2f", totalRevenue));
            stats.put("totalCourses", uniqueCourses.size());
            stats.put("avgFees", String.format("%.2f", avgFees));

        } catch (Exception e) {
            log.error("Error calculating statistics", e);
            stats.put("totalStudents", 0);
            stats.put("totalRevenue", "0.00");
            stats.put("totalCourses", 0);
            stats.put("avgFees", "0.00");
        }

        return stats;
    }

    /**
     * Export course sales report
     */
    @Transactional(readOnly = true)
    public List<CourseSalesReportDTO> exportCourseSalesReport(
            Long courseId, LocalDate startDate, LocalDate endDate) {

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("Course not found"));

        List<Admission> admissions = findAdmissionsList(
                course.getCourseName(), startDate, endDate);

        double totalAmount = calculateTotalAmount(admissions);

        return admissions.stream()
                .map(a -> convertToDTO(a, getFeesForAdmission(a), totalAmount))
                .collect(Collectors.toList());
    }

    // Helper methods

    private List<Admission> findAdmissionsList(
            String courseName, LocalDate startDate, LocalDate endDate) {

        // Use native query to avoid pagination issues
        List<Admission> allAdmissions = admissionRepository.findAll().stream()
                .filter(a -> !a.getIsDeleted())
                .collect(Collectors.toList());

        return allAdmissions.stream()
                .filter(admission -> {
                    // Filter by course
                    boolean hasCourse = admission.getCourses() != null &&
                            admission.getCourses().stream()
                                    .anyMatch(c -> c.equalsIgnoreCase(courseName));

                    // Filter by date range
                    boolean inDateRange = admission.getAdmissionDate() != null &&
                            !admission.getAdmissionDate().isBefore(startDate) &&
                            !admission.getAdmissionDate().isAfter(endDate);

                    return hasCourse && inDateRange;
                })
                .collect(Collectors.toList());
    }

    private List<Admission> filterBySearch(List<Admission> admissions, String searchValue) {
        if (searchValue == null || searchValue.trim().isEmpty()) {
            return admissions;
        }

        String search = searchValue.toLowerCase();

        return admissions.stream()
                .filter(admission -> {
                    String regNo = admission.getRegistrationNumber() != null ?
                            admission.getRegistrationNumber().toLowerCase() : "";
                    String name = admission.getFullName() != null ?
                            admission.getFullName().toLowerCase() : "";
                    String mobile = admission.getMobilePrimary() != null ?
                            admission.getMobilePrimary().toLowerCase() : "";

                    return regNo.contains(search) || name.contains(search) || mobile.contains(search);
                })
                .collect(Collectors.toList());
    }


    private double calculateTotalAmount(List<Admission> admissions) {
        double totalAmount = 0.0;

        for (Admission admission : admissions) {
            Double paidAmount = getTotalPaidFromFeesTable(admission.getRegistrationNumber());
            if (paidAmount != null) {
                totalAmount += paidAmount;
            }
        }

        log.info("✅ Calculated total paid amount: ₹{} for {} admissions", totalAmount, admissions.size());
        return totalAmount;
    }

    private CourseSalesReportDTO convertToDTO(Admission admission, Double studentFees, double totalCourseAmount) {
        if (studentFees == null) {
            studentFees = 0.0;
        }

        return CourseSalesReportDTO.builder()
                .registrationNo(admission.getRegistrationNumber())
                .studentName(admission.getFullName())
                .studentMobileNo(admission.getMobilePrimary())
                .createdDate(formatDate(admission.getAdmissionDate()))
                .compareDate(admission.getAdmissionDate())
                .courseAmount(formatCurrency(studentFees))  // ✅ Individual student fees
                .totalCourseAmount(formatCurrency(totalCourseAmount))  // ✅ Grand total
                .build();
    }

    private String formatDate(LocalDate date) {
        if (date == null) return "";
        return date.format(DATE_FORMATTER);
    }

    private String formatCurrency(Double amount) {
        if (amount == null) return "₹0.00";
        return String.format("₹%.2f", amount);
    }
}