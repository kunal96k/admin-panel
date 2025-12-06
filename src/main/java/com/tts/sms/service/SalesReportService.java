package com.tts.sms.service;

import com.tts.sms.dto.CourseSalesReportDTO;
import com.tts.sms.dto.DataTablesRequest;
import com.tts.sms.dto.DataTablesResponse;
import com.tts.sms.model.Admission;
import com.tts.sms.model.Course;
import com.tts.sms.model.Fees;
import com.tts.sms.repository.AdmissionRepository;
import com.tts.sms.repository.CourseRepository;
import com.tts.sms.repository.FeesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class SalesReportService {

    private final AdmissionRepository admissionRepository;
    private final CourseRepository courseRepository;
    private final FeesRepository feesRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MMM-yyyy");

    /**
     * Get course wise sales report with DataTables server-side processing
     */
    @Transactional(readOnly = true)
    public DataTablesResponse<CourseSalesReportDTO> getCourseSalesReport(
            Long courseId, LocalDate startDate, LocalDate endDate, DataTablesRequest request) {

        log.debug("Generating course sales report - Course: {}, Start: {}, End: {}",
                courseId, startDate, endDate);

        // Validate inputs
        if (courseId == null) {
            throw new IllegalArgumentException("Course ID is required");
        }
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Start date and end date are required");
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date must be before end date");
        }

        // Get course name
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("Course not found: " + courseId));

        // Build pagination and sorting
        Sort sort = buildSort(request);
        Pageable pageable = PageRequest.of(
                request.getStart() / request.getLength(),
                request.getLength(),
                sort
        );

        // Get all admissions for the course in date range
        List<Admission> allAdmissions = findAdmissionsByCourseAndDateRange(
                course.getCourseName(), startDate, endDate);

        // Filter by search if provided
        List<Admission> filteredAdmissions = filterBySearch(allAdmissions, request.getSearchValue());

        //  FIXED: Calculate total amount ONCE for all filtered admissions
        double totalAmount = calculateTotalAmount(filteredAdmissions);

        log.info("📊 Total filtered admissions: {}, Total Amount: ₹{}",
                filteredAdmissions.size(), totalAmount);

        // Apply pagination manually
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filteredAdmissions.size());
        List<Admission> pagedAdmissions = filteredAdmissions.subList(start, end);

        //  FIXED: Pass totalAmount to each DTO
        List<CourseSalesReportDTO> data = pagedAdmissions.stream()
                .map(admission -> convertToDTO(admission, totalAmount))
                .collect(Collectors.toList());

        // Build response
        DataTablesResponse<CourseSalesReportDTO> response = new DataTablesResponse<>();
        response.setDraw(request.getDraw());
        response.setRecordsTotal(allAdmissions.size());
        response.setRecordsFiltered(filteredAdmissions.size());
        response.setData(data);

        log.info(" Generated report: {} records on page, Total: ₹{}", data.size(), totalAmount);

        return response;
    }

    /**
     * Get total fees from Fees table by registration number
     */
    private Double getTotalFeesFromFeesTable(String registrationNumber) {
        return feesRepository.findByRegistrationNumberAndIsDeletedFalse(registrationNumber)
                .map(Fees::getTotalFees)
                .orElse(0.0);
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
                    admissions = findAdmissionsByCourseAndDateRange(
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

        List<Admission> admissions = findAdmissionsByCourseAndDateRange(
                course.getCourseName(), startDate, endDate);

        double totalAmount = calculateTotalAmount(admissions);

        return admissions.stream()
                .map(admission -> convertToDTO(admission, totalAmount))
                .collect(Collectors.toList());
    }

    // Helper methods

    private List<Admission> findAdmissionsByCourseAndDateRange(
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
                .sorted(Comparator.comparing(Admission::getAdmissionDate).reversed())
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

    private Sort buildSort(DataTablesRequest request) {
        String[] columns = {
                "registration_number",  // Column 0
                "first_name",          // Column 1 (from admissions table)
                "mobile_primary",      // Column 2
                "admission_date",      // Column 3
                "total_receivable_fees" // Column 4
        };

        int columnIndex = request.getOrderColumn();
        if (columnIndex < 0 || columnIndex >= columns.length) {
            columnIndex = 3;
        }

        String sortColumn = columns[columnIndex];
        Sort.Direction direction = "asc".equalsIgnoreCase(request.getOrderDir()) ?
                Sort.Direction.ASC : Sort.Direction.DESC;

        return Sort.by(direction, sortColumn);
    }

    private double calculateTotalAmount(List<Admission> admissions) {
        double totalAmount = 0.0;

        for (Admission admission : admissions) {
            // ✅ Get total fees from Fees table
            Double feesFromTable = getTotalFeesFromFeesTable(admission.getRegistrationNumber());

            // Fallback to admission's totalReceivableFees if Fees table is empty
            if (feesFromTable == null || feesFromTable == 0.0) {
                feesFromTable = admission.getTotalReceivableFees() != null
                        ? admission.getTotalReceivableFees()
                        : 0.0;
            }

            totalAmount += feesFromTable;

            log.debug("🔹 RegNo: {} - Fees: ₹{}",
                    admission.getRegistrationNumber(), feesFromTable);
        }

        log.info("✅ Calculated total amount: ₹{} for {} admissions", totalAmount, admissions.size());
        return totalAmount;
    }

    private CourseSalesReportDTO convertToDTO(Admission admission, double totalCourseAmount) {
        // ✅ Get total fees from Fees table
        Double studentFees = getTotalFeesFromFeesTable(admission.getRegistrationNumber());

        // Fallback to admission if Fees table doesn't have data
        if (studentFees == null || studentFees == 0.0) {
            studentFees = admission.getTotalReceivableFees();
            log.warn("⚠️ RegNo: {} - Using admission fees: ₹{}",
                    admission.getRegistrationNumber(), studentFees);
        } else {
            log.debug("✅ RegNo: {} - Using Fees table: ₹{}",
                    admission.getRegistrationNumber(), studentFees);
        }

        // Ensure studentFees is never null
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