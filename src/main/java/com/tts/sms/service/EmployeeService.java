package com.tts.sms.service;

import com.tts.sms.dto.EmployeeRequestDTO;
import com.tts.sms.dto.EmployeeResponseDTO;
import com.tts.sms.dto.MenuPermissionDTO;
import com.tts.sms.exception.DuplicateResourceException;
import com.tts.sms.exception.ResourceNotFoundException;
import com.tts.sms.exception.UnauthorizedException;
import com.tts.sms.model.*;
import com.tts.sms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final RoleRepository roleRepository;
    private final MenuRepository menuRepository;
    private final EmployeeMenuPermissionRepository employeeMenuPermissionRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final EmailTemplateService emailTemplateService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Value("${app.base-url:http://localhost:8081}")
    private String baseUrl;

    private static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";
    private static final String ADMIN_ROLE = "ADMIN";

    /**
     * Get current logged-in user
     */
    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User) {
            return (User) authentication.getPrincipal();
        }
        throw new UnauthorizedException("No authenticated user found");
    }

    /**
     * Check if current user is SUPER_ADMIN
     */
    private boolean isSuperAdmin() {
        try {
            User currentUser = getCurrentUser();
            String roleTitle = currentUser.getEmployee().getRole().getRoleTitle();
            return SUPER_ADMIN_ROLE.equalsIgnoreCase(roleTitle);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if target employee is SUPER_ADMIN
     */
    private boolean isTargetSuperAdmin(Employee employee) {
        return employee.getRole() != null &&
                SUPER_ADMIN_ROLE.equalsIgnoreCase(employee.getRole().getRoleTitle());
    }

    /**
     * Validate if current user can modify target employee
     */
    private void validateModificationPermission(Employee targetEmployee, String operation) {
        User currentUser = getCurrentUser();
        Employee currentEmployee = currentUser.getEmployee();

        log.info("🔒 Permission check - Current User: {} (Role: {}), Target: {} (Role: {}), Operation: {}",
                currentEmployee.getEmployeeName(),
                currentEmployee.getRole().getRoleTitle(),
                targetEmployee.getEmployeeName(),
                targetEmployee.getRole().getRoleTitle(),
                operation);

        // Rule 1: Only SUPER_ADMIN can modify SUPER_ADMIN accounts
        if (isTargetSuperAdmin(targetEmployee) && !isSuperAdmin()) {
            log.warn("⚠️ Unauthorized attempt to {} SUPER_ADMIN by non-SUPER_ADMIN user", operation);
            throw new UnauthorizedException(
                    "Access Denied: Only SUPER_ADMIN can " + operation + " SUPER_ADMIN accounts. " +
                            "This action has been logged for security purposes."
            );
        }

        // Rule 2: Prevent self-deletion
        if ("delete".equalsIgnoreCase(operation) &&
                currentEmployee.getId().equals(targetEmployee.getId())) {
            log.warn("⚠️ User attempted to delete their own account");
            throw new UnauthorizedException("You cannot delete your own account");
        }

        // Rule 3: Prevent downgrading own role
        if ("update".equalsIgnoreCase(operation) &&
                currentEmployee.getId().equals(targetEmployee.getId())) {
            log.warn("⚠️ User attempted to modify their own permissions");
            throw new UnauthorizedException(
                    "You cannot modify your own role or permissions. " +
                            "Please contact another administrator."
            );
        }

        log.info("✅ Permission check passed for {} operation", operation);
    }

    @Transactional
    public EmployeeResponseDTO createEmployee(EmployeeRequestDTO requestDTO, MultipartFile photoFile) {
        log.info("📝 Creating employee: {}", requestDTO.getEmployeeName());

        // Validate: Only SUPER_ADMIN can create other SUPER_ADMIN accounts
        Role targetRole = roleRepository.findById(requestDTO.getRoleId())
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));

        if (SUPER_ADMIN_ROLE.equalsIgnoreCase(targetRole.getRoleTitle()) && !isSuperAdmin()) {
            throw new UnauthorizedException(
                    "Access Denied: Only SUPER_ADMIN can create SUPER_ADMIN accounts"
            );
        }

        if (employeeRepository.existsByEmailId(requestDTO.getEmailId())) {
            throw new DuplicateResourceException("Email already exists: " + requestDTO.getEmailId());
        }

        String plainPassword = null;
        if (requestDTO.getUsername() != null && !requestDTO.getUsername().trim().isEmpty()) {
            if (userRepository.existsByUsername(requestDTO.getUsername())) {
                throw new DuplicateResourceException("Username already exists: " + requestDTO.getUsername());
            }

            if (requestDTO.getPassword() == null ||
                    !requestDTO.getPassword().equals(requestDTO.getConfirmPassword())) {
                throw new IllegalArgumentException("Password and Confirm Password do not match");
            }

            if (!validatePasswordStrength(requestDTO.getPassword())) {
                throw new IllegalArgumentException("Password does not meet security requirements");
            }

            plainPassword = requestDTO.getPassword();
        }

        Employee employee = new Employee();
        mapDTOToEntity(requestDTO, employee, photoFile);
        Employee savedEmployee = employeeRepository.save(employee);
        log.info("✅ Employee saved with ID: {}", savedEmployee.getId());

        if (requestDTO.getUsername() != null && !requestDTO.getUsername().trim().isEmpty()) {
            createUserCredentials(savedEmployee, requestDTO);

            try {
                emailTemplateService.sendCredentialsEmail(
                        savedEmployee.getEmailId(),
                        savedEmployee.getEmployeeName(),
                        requestDTO.getUsername(),
                        plainPassword
                );
            } catch (Exception e) {
                log.warn("⚠️ Failed to send credentials email: {}", e.getMessage());
            }
        }

        if (requestDTO.getMenuPermissions() != null && !requestDTO.getMenuPermissions().isEmpty()) {
            saveMenuPermissions(savedEmployee, requestDTO.getMenuPermissions());
        }

        return convertToResponseDTO(savedEmployee);
    }

    @Transactional
    public EmployeeResponseDTO updateEmployee(Long id, EmployeeRequestDTO requestDTO, MultipartFile photoFile) {
        log.info("✏️ Updating employee ID: {}", id);

        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + id));

        // 🔒 SECURITY CHECK
        validateModificationPermission(employee, "update");

        // Validate: Only SUPER_ADMIN can change role to SUPER_ADMIN
        Role targetRole = roleRepository.findById(requestDTO.getRoleId())
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));

        if (SUPER_ADMIN_ROLE.equalsIgnoreCase(targetRole.getRoleTitle()) && !isSuperAdmin()) {
            throw new UnauthorizedException(
                    "Access Denied: Only SUPER_ADMIN can assign SUPER_ADMIN role"
            );
        }

        if (employeeRepository.existsByEmailIdAndIdNot(requestDTO.getEmailId(), id)) {
            throw new DuplicateResourceException("Email already exists: " + requestDTO.getEmailId());
        }

        if (requestDTO.getUsername() != null && !requestDTO.getUsername().trim().isEmpty()) {
            userRepository.findByEmployee(employee).ifPresentOrElse(
                    existingUser -> {
                        if (!existingUser.getUsername().equals(requestDTO.getUsername())) {
                            if (userRepository.existsByUsername(requestDTO.getUsername())) {
                                throw new DuplicateResourceException("Username already exists: " + requestDTO.getUsername());
                            }
                            existingUser.setUsername(requestDTO.getUsername());
                        }

                        if (requestDTO.getPassword() != null && !requestDTO.getPassword().trim().isEmpty()) {
                            if (!requestDTO.getPassword().equals(requestDTO.getConfirmPassword())) {
                                throw new IllegalArgumentException("Password and Confirm Password do not match");
                            }

                            if (!validatePasswordStrength(requestDTO.getPassword())) {
                                throw new IllegalArgumentException("Password does not meet security requirements");
                            }

                            existingUser.setPassword(passwordEncoder.encode(requestDTO.getPassword()));

                            emailTemplateService.sendPasswordChangedEmail(
                                    employee.getEmailId(),
                                    employee.getEmployeeName()
                            );
                        }

                        existingUser.setRole(targetRole);
                        userRepository.save(existingUser);
                    },
                    () -> createUserCredentials(employee, requestDTO)
            );
        }

        mapDTOToEntity(requestDTO, employee, photoFile);
        Employee updatedEmployee = employeeRepository.save(employee);

        if (requestDTO.getMenuPermissions() != null && !requestDTO.getMenuPermissions().isEmpty()) {
            employeeMenuPermissionRepository.deleteByEmployeeId(updatedEmployee.getId());
            saveMenuPermissions(updatedEmployee, requestDTO.getMenuPermissions());
        }

        return convertToResponseDTO(updatedEmployee);
    }

    @Transactional
    public void deleteEmployee(Long id) {
        log.info("🗑️ Deleting employee ID: {}", id);

        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + id));

        // 🔒 SECURITY CHECK
        validateModificationPermission(employee, "delete");

        // Delete in correct order to avoid FK constraint violations
        userRepository.findByEmployee(employee).ifPresent(user -> {
            log.info("Deleting user credentials for employee: {}", id);
            userRepository.delete(user);
        });

        log.info("Deleting menu permissions for employee: {}", id);
        employeeMenuPermissionRepository.deleteByEmployeeId(id);

        if (employee.getPhoto() != null && !employee.getPhoto().isEmpty()) {
            log.info("Deleting employee photo: {}", employee.getPhoto());
            fileStorageService.deleteFile(employee.getPhoto(), "employees");
        }

        employeeRepository.delete(employee);
        log.info("✅ Employee deleted successfully: {}", id);
    }

    private void mapDTOToEntity(EmployeeRequestDTO dto, Employee employee, MultipartFile photoFile) {
        employee.setEmployeeName(dto.getEmployeeName());
        employee.setMobileNumber(dto.getMobileNumber());
        employee.setEmailId(dto.getEmailId());
        employee.setDesignation(dto.getDesignation());
        employee.setGender(dto.getGender());
        employee.setDateOfBirth(dto.getDateOfBirth());
        employee.setAddress(dto.getAddress());
        employee.setZoomLink(dto.getZoomLink());

        Role role = roleRepository.findById(dto.getRoleId())
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));
        employee.setRole(role);

        if (photoFile != null && !photoFile.isEmpty()) {
            try {
                if (employee.getPhoto() != null && !employee.getPhoto().isEmpty()) {
                    fileStorageService.deleteFile(employee.getPhoto(), "employees");
                }

                String filename = fileStorageService.storeFile(photoFile, "employees");
                employee.setPhoto(filename);
                log.info("✅ Photo saved: {}", filename);
            } catch (Exception e) {
                log.error("❌ Error saving photo: {}", e.getMessage());
                throw new RuntimeException("Failed to save employee photo: " + e.getMessage());
            }
        }

        // Mobile permissions
        employee.setNewAdmission(dto.getNewAdmission() != null && dto.getNewAdmission());
        employee.setViewAdmission(dto.getViewAdmission() != null && dto.getViewAdmission());
        employee.setNewEnquiry(dto.getNewEnquiry() != null && dto.getNewEnquiry());
        employee.setViewEnquiry(dto.getViewEnquiry() != null && dto.getViewEnquiry());
        employee.setFeeManager(dto.getFeeManager() != null && dto.getFeeManager());
        employee.setManageCourse(dto.getManageCourse() != null && dto.getManageCourse());
        employee.setManageBatch(dto.getManageBatch() != null && dto.getManageBatch());
    }

    private void createUserCredentials(Employee employee, EmployeeRequestDTO requestDTO) {
        log.info("🔐 Creating user credentials for: {}", requestDTO.getUsername());

        User user = new User();
        user.setUsername(requestDTO.getUsername());
        user.setPassword(passwordEncoder.encode(requestDTO.getPassword()));
        user.setEmployee(employee);
        user.setRole(employee.getRole());
        user.setIsActive(true);
        user.setIsLocked(false);
        user.setFailedAttempts(0);

        userRepository.save(user);
        log.info("✅ User credentials created successfully");
    }

    private boolean validatePasswordStrength(String password) {
        if (password == null || password.length() < 8) return false;

        boolean hasUpper = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLower = password.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasSpecial = password.chars().anyMatch(ch -> "!@#$%^&*(),.?\":{}|<>".indexOf(ch) >= 0);

        return hasUpper && hasLower && hasDigit && hasSpecial;
    }

    @Transactional
    private void saveMenuPermissions(Employee employee, List<MenuPermissionDTO> permissions) {
        for (MenuPermissionDTO permDto : permissions) {
            Menu menu = menuRepository.findById(permDto.getMenuId())
                    .orElseThrow(() -> new ResourceNotFoundException("Menu not found: " + permDto.getMenuId()));

            EmployeeMenuPermission permission = new EmployeeMenuPermission();
            permission.setEmployee(employee);
            permission.setMenu(menu);
            permission.setHasAccess(permDto.getHasAccess());

            employeeMenuPermissionRepository.save(permission);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAllEmployees(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdDate").descending());
        Page<Employee> employeePage = employeeRepository.findAll(pageable);
        return buildResponse(employeePage);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> searchEmployees(String searchTerm, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdDate").descending());
        Page<Employee> employeePage = employeeRepository.searchEmployees(searchTerm, pageable);
        return buildResponse(employeePage);
    }

    @Transactional(readOnly = true)
    public EmployeeResponseDTO getEmployeeById(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + id));
        return convertToResponseDTO(employee);
    }

    @Transactional
    public void sendCredentialsEmail(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + id));

        User user = userRepository.findByEmployee(employee)
                .orElseThrow(() -> new IllegalStateException("Employee does not have login credentials"));

        emailTemplateService.sendPasswordChangedEmail(
                employee.getEmailId(),
                employee.getEmployeeName()
        );

        log.info("📧 Credentials email sent to: {}", employee.getEmailId());
    }

    private EmployeeResponseDTO convertToResponseDTO(Employee employee) {
        String photoUrl = null;
        if (employee.getPhoto() != null && !employee.getPhoto().isEmpty()) {
            photoUrl = baseUrl + "/api/files/employees/" + employee.getPhoto();
        }

        return EmployeeResponseDTO.builder()
                .id(employee.getId())
                .employeeName(employee.getEmployeeName())
                .mobileNumber(employee.getMobileNumber())
                .emailId(employee.getEmailId())
                .designation(employee.getDesignation())
                .gender(employee.getGender())
                .dateOfBirth(employee.getDateOfBirth() != null ? employee.getDateOfBirth().toString() : null)
                .address(employee.getAddress())
                .roleId(employee.getRole() != null ? employee.getRole().getId() : null)
                .roleName(employee.getRole() != null ? employee.getRole().getRoleTitle() : null)
                .zoomLink(employee.getZoomLink())
                .photoFilename(employee.getPhoto())
                .photoUrl(photoUrl)
                .isActive(employee.getIsActive())
                .createdDate(employee.getCreatedDate())
                .build();
    }

    private Map<String, Object> buildResponse(Page<Employee> page) {
        Map<String, Object> response = new HashMap<>();
        response.put("employees", page.getContent().stream()
                .map(this::convertToResponseDTO).toList());
        response.put("currentPage", page.getNumber());
        response.put("totalItems", page.getTotalElements());
        response.put("totalPages", page.getTotalPages());
        return response;
    }
}