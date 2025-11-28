package com.tts.sms.service;

import com.tts.sms.dto.*;
import com.tts.sms.model.Course;
import com.tts.sms.model.Package;
import com.tts.sms.repository.CourseRepository;
import com.tts.sms.repository.PackageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PackageService {

    private final PackageRepository packageRepository;
    private final CourseRepository courseRepository;

    @Transactional(readOnly = true)
    public List<PackageDTO> getAllPackages() {
        return packageRepository.findAll()
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<PackageDTO> getPackagesPaginated(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return packageRepository.findAll(pageable)
                .map(this::convertToDTO);
    }

    @Transactional(readOnly = true)
    public Page<PackageDTO> searchPackages(String searchTerm, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return packageRepository.searchPackages(searchTerm, pageable)
                .map(this::convertToDTO);
    }

    @Transactional(readOnly = true)
    public PackageDTO getPackageById(Long id) {
        Package pkg = packageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Package not found with id: " + id));
        return convertToDTO(pkg);
    }

    public PackageDTO createPackage(PackageCreateRequest request) {
        if (request.getPackageName() == null || request.getPackageName().trim().isEmpty()) {
            throw new RuntimeException("Package name is required");
        }

        if (request.getCourseIds() == null || request.getCourseIds().isEmpty()) {
            throw new RuntimeException("At least one course must be selected");
        }

        if (packageRepository.existsByPackageNameIgnoreCaseAndIsActiveTrue(request.getPackageName())) {
            throw new RuntimeException("Package name already exists: " + request.getPackageName());
        }

        List<Course> courses = courseRepository.findAllById(request.getCourseIds());
        if (courses.size() != request.getCourseIds().size()) {
            throw new RuntimeException("One or more courses not found");
        }

        courses = courses.stream()
                .filter(Course::getIsActive)
                .collect(Collectors.toList());

        if (courses.isEmpty()) {
            throw new RuntimeException("No active courses found");
        }

        // Use the total amount from request
        BigDecimal totalAmount = request.getTotalAmount() != null ?
                request.getTotalAmount() : BigDecimal.ZERO;

        Package pkg = new Package();
        pkg.setPackageName(request.getPackageName().trim());
        pkg.setTotalAmount(totalAmount);
        pkg.setIsActive(true);
        pkg.setCourses(courses);

        Package savedPackage = packageRepository.save(pkg);
        log.info("Package created successfully: {}", savedPackage.getPackageName());
        return convertToDTO(savedPackage);
    }

    public PackageDTO updatePackage(Long id, PackageUpdateRequest request) {
        Package pkg = packageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Package not found with id: " + id));

        if (request.getPackageName() == null || request.getPackageName().trim().isEmpty()) {
            throw new RuntimeException("Package name is required");
        }

        if (request.getCourseIds() == null || request.getCourseIds().isEmpty()) {
            throw new RuntimeException("At least one course must be selected");
        }

        if (packageRepository.existsByPackageNameExcludingId(request.getPackageName(), id)) {
            throw new RuntimeException("Package name already exists: " + request.getPackageName());
        }

        List<Course> courses = courseRepository.findAllById(request.getCourseIds());
        if (courses.size() != request.getCourseIds().size()) {
            throw new RuntimeException("One or more courses not found");
        }

        courses = courses.stream()
                .filter(Course::getIsActive)
                .collect(Collectors.toList());

        if (courses.isEmpty()) {
            throw new RuntimeException("No active courses found");
        }

        // Use the total amount from request
        BigDecimal totalAmount = request.getTotalAmount() != null ?
                request.getTotalAmount() : BigDecimal.ZERO;

        pkg.setPackageName(request.getPackageName().trim());
        pkg.setTotalAmount(totalAmount);
        pkg.setCourses(courses);

        Package updatedPackage = packageRepository.save(pkg);
        log.info("Package updated successfully: {}", updatedPackage.getPackageName());
        return convertToDTO(updatedPackage);
    }

    public void deletePackage(Long id) {
        Package pkg = packageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Package not found with id: " + id));

        packageRepository.delete(pkg);
        log.info("Package deleted permanently: {}", pkg.getPackageName());
    }

    @Transactional(readOnly = true)
    public List<PackageCSVExportDTO> exportPackagesToCSV() {
        List<Package> packages = packageRepository.findAll();

        return packages.stream()
                .map(pkg -> {
                    int index = packages.indexOf(pkg) + 1;
                    String courseNames = pkg.getCourses().stream()
                            .filter(Course::getIsActive)
                            .map(Course::getCourseName)
                            .collect(Collectors.joining(", "));

                    return new PackageCSVExportDTO(
                            index,
                            pkg.getPackageName(),
                            courseNames,
                            pkg.getTotalAmount()
                    );
                })
                .collect(Collectors.toList());
    }

    private PackageDTO convertToDTO(Package pkg) {
        PackageDTO dto = new PackageDTO();
        dto.setId(pkg.getId());
        dto.setPackageName(pkg.getPackageName());
        dto.setTotalAmount(pkg.getTotalAmount());
        dto.setIsActive(pkg.getIsActive());
        dto.setCreatedAt(pkg.getCreatedAt());
        dto.setUpdatedAt(pkg.getUpdatedAt());

        if (pkg.getCourses() != null) {
            List<CourseDTO> courseDTOs = pkg.getCourses().stream()
                    .filter(Course::getIsActive)
                    .map(this::convertCourseToDTO)
                    .collect(Collectors.toList());
            dto.setCourses(courseDTOs);
        }

        return dto;
    }

    private CourseDTO convertCourseToDTO(Course course) {
        CourseDTO dto = new CourseDTO();
        dto.setId(course.getId());
        dto.setCourseName(course.getCourseName());
        dto.setCourseFees(course.getCourseFees());
        dto.setCourseImagePath(course.getCourseImagePath());
        dto.setIsActive(course.getIsActive());
        dto.setCreatedAt(course.getCreatedAt());
        dto.setUpdatedAt(course.getUpdatedAt());
        return dto;
    }
}