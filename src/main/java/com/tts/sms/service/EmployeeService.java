package com.tts.sms.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

import com.tts.sms.dto.EmployeeRequestDTO;
import com.tts.sms.dto.EmployeeResponseDTO;
import com.tts.sms.dto.MenuPermissionDTO;
import com.tts.sms.exception.DuplicateResourceException;
import com.tts.sms.exception.ResourceNotFoundException;
import com.tts.sms.exception.UnauthorizedException;
import com.tts.sms.model.Employee;
import com.tts.sms.model.EmployeeMenuPermission;
import com.tts.sms.model.Menu;
import com.tts.sms.model.Role;
import com.tts.sms.model.User;
import com.tts.sms.repository.EmployeeMenuPermissionRepository;
import com.tts.sms.repository.EmployeeRepository;
import com.tts.sms.repository.MenuRepository;
import com.tts.sms.repository.RoleRepository;
import com.tts.sms.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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

        log.info("[SECURE] Permission check - Current User: {} (Role: {}), Target: {} (Role: {}), Operation: {}",
                currentEmployee.getEmployeeName(),
                currentEmployee.getRole().getRoleTitle(),
                targetEmployee.getEmployeeName(),
                targetEmployee.getRole().getRoleTitle(),
                operation);

        // NEW RULE: Only SUPER_ADMIN can perform ANY modification (update, delete, lock, etc)
        if (!"view".equalsIgnoreCase(operation) && !isSuperAdmin()) {
            log.warn("[WARN] SECURITY ALERT: Non-SUPER_ADMIN {} attempted to {} account {}",
                    currentEmployee.getEmployeeName(), operation, targetEmployee.getEmployeeName());
            throw new UnauthorizedException(
                    "[BLOCKED] Access Denied: Only SUPER_ADMIN can perform this action. " +
                            "You only have permission to view employee details."
            );
        }

        // Rule 1: Only SUPER_ADMIN can modify SUPER_ADMIN accounts (redundant now but safe)
        if (isTargetSuperAdmin(targetEmployee) && !isSuperAdmin()) {
            log.warn("[WARN] SECURITY ALERT: User {} attempted to {} SUPER_ADMIN account {}",
                    currentEmployee.getEmployeeName(), operation, targetEmployee.getEmployeeName());
            throw new UnauthorizedException(
                    "[BLOCKED] Access Denied: Only SUPER_ADMIN can " + operation + " SUPER_ADMIN accounts. " +
                            "This security violation has been logged."
            );
        }

        // Rule 2: Prevent self-deletion
        if ("delete".equalsIgnoreCase(operation) &&
                currentEmployee.getId().equals(targetEmployee.getId()) && !isSuperAdmin() ) {
            log.warn("[WARN] User {} attempted to delete their own account", currentEmployee.getEmployeeName());
            throw new UnauthorizedException(
                    "[BLOCKED] Security Policy: You cannot delete your own account. " +
                            "Please contact another administrator."
            );
        }

        // Rule 3: Prevent self-modification of role/permissions
        if ("update".equalsIgnoreCase(operation) &&
                currentEmployee.getId().equals(targetEmployee.getId()) && !isSuperAdmin() ) {
            log.warn("[WARN] User {} attempted to modify their own role/permissions",
                    currentEmployee.getEmployeeName());
            throw new UnauthorizedException(
                    "[BLOCKED] Security Policy: You cannot modify your own role or permissions. " +
                            "Contact SUPER_ADMIN for changes to your account."
            );
        }

        // Rule 4: Prevent self-locking or deactivation (Super Admin Protection)
        if (("lock".equalsIgnoreCase(operation) || "deactivate".equalsIgnoreCase(operation)) &&
                currentEmployee.getId().equals(targetEmployee.getId())) {
            log.warn("[WARN] User {} attempted to {} their own account",
                    currentEmployee.getEmployeeName(), operation);
            throw new UnauthorizedException(
                    "[BLOCKED] Security Policy: You cannot " + operation + " your own account. " +
                            "This safety measure prevents you from accidentally locking yourself out of the system."
            );
        }

        log.info(" Permission check passed for {} operation", operation);
    }

    /**
     * Check if user can perform operation before loading data
     */
    public Map<String, Object> canModifyEmployee(Long employeeId) {
        Map<String, Object> result = new HashMap<>();

        try {
            Employee targetEmployee = employeeRepository.findById(employeeId)
                    .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

            // Log security check
            User currentUser = getCurrentUser();
            log.info("[SECURE] Security Check: User {} attempting to access employee {} (ID: {})",
                    currentUser.getUsername(),
                    targetEmployee.getEmployeeName(),
                    employeeId);

            // Change to check 'update' instead of 'view' so frontend gets actual modification permissions
            validateModificationPermission(targetEmployee, "update");

            result.put("canModify", true);
            result.put("canDelete", true);
            result.put("message", " Access granted");

            log.info(" Permission granted for modify operation");

        } catch (UnauthorizedException e) {
            result.put("canModify", false);
            result.put("canDelete", false);
            result.put("message", e.getMessage());

            log.warn("[WARN] Permission denied: {}", e.getMessage());
        }

        return result;
    }

    @Transactional
    public EmployeeResponseDTO createEmployee(EmployeeRequestDTO requestDTO, MultipartFile photoFile) {
        log.info("[NOTE] Creating employee: {}", requestDTO.getEmployeeName());

        // Security: Only SUPER_ADMIN can create employees
        if (!isSuperAdmin()) {
            log.warn("[WARN] SECURITY ALERT: Non-SUPER_ADMIN attempted to create an account");
            throw new UnauthorizedException(
                    "[BLOCKED] Access Denied: Only SUPER_ADMIN can create new employees."
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
        log.info(" Employee saved with ID: {}", savedEmployee.getId());

        if (requestDTO.getUsername() != null && !requestDTO.getUsername().trim().isEmpty()) {
            createUserCredentials(savedEmployee, requestDTO);

            // SYNC to Employee entity
            savedEmployee.setUsername(requestDTO.getUsername());
            savedEmployee.setPassword(passwordEncoder.encode(requestDTO.getPassword()));
            employeeRepository.save(savedEmployee);

            try {
                emailTemplateService.sendCredentialsEmail(
                        savedEmployee.getEmailId(),
                        savedEmployee.getEmployeeName(),
                        requestDTO.getUsername(),
                        plainPassword
                );
            } catch (Exception e) {
                log.warn("[WARN] Failed to send credentials email: {}", e.getMessage());
            }
        }

        if (requestDTO.getMenuPermissions() != null && !requestDTO.getMenuPermissions().isEmpty()) {
            saveMenuPermissions(savedEmployee, requestDTO.getMenuPermissions());
        }

        return convertToResponseDTO(savedEmployee);
    }

    @Transactional
    public EmployeeResponseDTO updateEmployee(Long id, EmployeeRequestDTO requestDTO, MultipartFile photoFile) {
        log.info("[EDIT] Updating employee ID: {}", id);

        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + id));

        // [SECURE] CRITICAL SECURITY CHECK
        validateModificationPermission(employee, "update");

        // Security: Only SUPER_ADMIN can assign SUPER_ADMIN role
        Role targetRole = roleRepository.findById(requestDTO.getRoleId())
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));

        if (SUPER_ADMIN_ROLE.equalsIgnoreCase(targetRole.getRoleTitle()) && !isSuperAdmin()) {
            log.warn("[WARN] SECURITY ALERT: Non-SUPER_ADMIN attempted to assign SUPER_ADMIN role");
            throw new UnauthorizedException(
                    "[BLOCKED] Access Denied: Only SUPER_ADMIN can assign SUPER_ADMIN role. " +
                            "This security violation has been logged."
            );
        }

        // Security: Prevent downgrading SUPER_ADMIN to regular role
        if (isTargetSuperAdmin(employee) &&
                !SUPER_ADMIN_ROLE.equalsIgnoreCase(targetRole.getRoleTitle()) &&
                !isSuperAdmin()) {
            throw new UnauthorizedException(
                    "[BLOCKED] Security Policy: Cannot downgrade SUPER_ADMIN role"
            );
        }

        if (employeeRepository.existsByEmailIdAndIdNot(requestDTO.getEmailId(), id)) {
            throw new DuplicateResourceException("Email already exists: " + requestDTO.getEmailId());
        }

        // Handle user credentials ONLY if username is provided
        if (requestDTO.getUsername() != null && !requestDTO.getUsername().trim().isEmpty()) {
            userRepository.findByEmployee(employee).ifPresentOrElse(
                    existingUser -> {
                        // Update username only if it's different
                        if (!existingUser.getUsername().equals(requestDTO.getUsername())) {
                            if (userRepository.existsByUsername(requestDTO.getUsername())) {
                                throw new DuplicateResourceException("Username already exists: " + requestDTO.getUsername());
                            }
                            existingUser.setUsername(requestDTO.getUsername());
                            employee.setUsername(requestDTO.getUsername());
                            log.info("[NOTE] Username updated for employee: {}", id);
                        }

                        // Update password ONLY if provided
                        if (requestDTO.getPassword() != null && !requestDTO.getPassword().trim().isEmpty()) {
                            if (!requestDTO.getPassword().equals(requestDTO.getConfirmPassword())) {
                                throw new IllegalArgumentException("Password and Confirm Password do not match");
                            }

                            if (!validatePasswordStrength(requestDTO.getPassword())) {
                                throw new IllegalArgumentException("Password does not meet security requirements");
                            }

                            String encodedPassword = passwordEncoder.encode(requestDTO.getPassword());
                            existingUser.setPassword(encodedPassword);
                            employee.setPassword(encodedPassword);
                            log.info("[AUTH] Password updated for employee: {}", id);

                            // Send email notification
                            try {
                                emailTemplateService.sendCredentialsEmail(
                                        employee.getEmailId(),
                                        employee.getEmployeeName(),
                                        existingUser.getUsername(),
                                        requestDTO.getPassword(),
                                        true
                                );
                            } catch (Exception e) {
                                log.warn("[WARN] Failed to send credentials email: {}", e.getMessage());
                            }
                        }

                        // Always update role
                        existingUser.setRole(targetRole);
                        userRepository.save(existingUser);
                        log.info(" User credentials updated successfully");
                    },
                    () -> {
                        // Create new credentials only if password is also provided
                        if (requestDTO.getPassword() != null && !requestDTO.getPassword().trim().isEmpty()) {
                            createUserCredentials(employee, requestDTO);
                            
                            // SYNC to employee
                            employee.setUsername(requestDTO.getUsername());
                            employee.setPassword(passwordEncoder.encode(requestDTO.getPassword()));
                            
                            log.info(" New user credentials created");
                        }
                    }
            );
        } else {
            // If checkbox is unchecked, only update role if user exists
            userRepository.findByEmployee(employee).ifPresent(existingUser -> {
                existingUser.setRole(targetRole);
                userRepository.save(existingUser);
                log.info(" User role updated (credentials unchanged)");
            });
        }

        // Update employee basic details
        mapDTOToEntity(requestDTO, employee, photoFile);
        Employee updatedEmployee = employeeRepository.save(employee);

        // Update menu permissions
        if (requestDTO.getMenuPermissions() != null && !requestDTO.getMenuPermissions().isEmpty()) {
            employeeMenuPermissionRepository.deleteByEmployeeId(updatedEmployee.getId());
            saveMenuPermissions(updatedEmployee, requestDTO.getMenuPermissions());
            log.info(" Menu permissions updated");
        }

        log.info(" Employee updated successfully: {}", id);
        return convertToResponseDTO(updatedEmployee);
    }

    @Transactional
    public void unlockAndActivateUser(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + employeeId));

        validateModificationPermission(employee, "unlock");
        if (!isSuperAdmin()) {
            throw new UnauthorizedException("[BLOCKED] Access Denied: Only SUPER_ADMIN can unlock/activate users");
        }

        User user = userRepository.findByEmployee(employee)
                .orElseThrow(() -> new IllegalStateException("Employee does not have login credentials"));

        user.setIsActive(true);
        user.setIsLocked(false);
        user.setFailedAttempts(0);
        user.setCaptchaAttempts(0);
        user.setCaptchaLockedUntil(null);
        user.setLastCaptchaFail(null);
        userRepository.save(user);

        log.info("[UNLOCKED] User unlocked & activated for employeeId={}, username={}", employeeId, user.getUsername());
    }

    @Transactional
    public void lockUser(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + employeeId));

        validateModificationPermission(employee, "lock");
        if (!isSuperAdmin()) {
            throw new UnauthorizedException("[BLOCKED] Access Denied: Only SUPER_ADMIN can lock users");
        }

        User user = userRepository.findByEmployee(employee)
                .orElseThrow(() -> new IllegalStateException("Employee does not have login credentials"));

        user.setIsLocked(true);
        userRepository.save(user);
        log.info("[SECURE] User locked for employeeId={}, username={}", employeeId, user.getUsername());
    }

    @Transactional
    public void unlockUser(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + employeeId));

        validateModificationPermission(employee, "unlock");
        if (!isSuperAdmin()) {
            throw new UnauthorizedException("[BLOCKED] Access Denied: Only SUPER_ADMIN can unlock users");
        }

        User user = userRepository.findByEmployee(employee)
                .orElseThrow(() -> new IllegalStateException("Employee does not have login credentials"));

        user.setIsLocked(false);
        user.setFailedAttempts(0);
        user.setCaptchaAttempts(0);
        user.setCaptchaLockedUntil(null);
        user.setLastCaptchaFail(null);
        userRepository.save(user);
        log.info("[UNLOCKED] User unlocked for employeeId={}, username={}", employeeId, user.getUsername());
    }

    @Transactional
    public void deactivateUser(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + employeeId));

        validateModificationPermission(employee, "deactivate");
        if (!isSuperAdmin()) {
            throw new UnauthorizedException("[BLOCKED] Access Denied: Only SUPER_ADMIN can deactivate users");
        }

        User user = userRepository.findByEmployee(employee)
                .orElseThrow(() -> new IllegalStateException("Employee does not have login credentials"));

        user.setIsActive(false);
        userRepository.save(user);
        log.info("[DENIED] User deactivated for employeeId={}, username={}", employeeId, user.getUsername());
    }

    @Transactional
    public void activateUser(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + employeeId));

        validateModificationPermission(employee, "activate");
        if (!isSuperAdmin()) {
            throw new UnauthorizedException("[BLOCKED] Access Denied: Only SUPER_ADMIN can activate users");
        }

        User user = userRepository.findByEmployee(employee)
                .orElseThrow(() -> new IllegalStateException("Employee does not have login credentials"));

        user.setIsActive(true);
        user.setFailedAttempts(0);
        userRepository.save(user);
        log.info("[OK] User activated for employeeId={}, username={}", employeeId, user.getUsername());
    }

    @Transactional
    public void deleteEmployee(Long id) {
        log.info("[DELETE] Attempting to delete employee ID: {}", id);

        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + id));

        // [SECURE] CRITICAL SECURITY CHECK
        validateModificationPermission(employee, "delete");

        // Additional check: Prevent deletion of last SUPER_ADMIN
        if (isTargetSuperAdmin(employee)) {
            long superAdminCount = employeeRepository.countByRoleRoleTitle(SUPER_ADMIN_ROLE);
            if (superAdminCount <= 1) {
                throw new UnauthorizedException(
                        "[BLOCKED] Security Policy: Cannot delete the last SUPER_ADMIN account. " +
                                "At least one SUPER_ADMIN must exist in the system."
                );
            }
        }

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
        log.info(" Employee deleted successfully: {}", id);
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
                log.info(" Photo saved: {}", filename);
            } catch (Exception e) {
                log.error("[FAIL] Error saving photo: {}", e.getMessage());
                throw new RuntimeException("Failed to save employee photo: " + e.getMessage());
            }
        }

        employee.setNewAdmission(dto.getNewAdmission() != null && dto.getNewAdmission());
        employee.setViewAdmission(dto.getViewAdmission() != null && dto.getViewAdmission());
        employee.setNewEnquiry(dto.getNewEnquiry() != null && dto.getNewEnquiry());
        employee.setViewEnquiry(dto.getViewEnquiry() != null && dto.getViewEnquiry());
        employee.setFeeManager(dto.getFeeManager() != null && dto.getFeeManager());
        employee.setManageCourse(dto.getManageCourse() != null && dto.getManageCourse());
        employee.setManageBatch(dto.getManageBatch() != null && dto.getManageBatch());
        employee.setAccDashboard(dto.getAccDashboard() != null && dto.getAccDashboard());
        employee.setCounsellorDash(dto.getCounsellorDash() != null && dto.getCounsellorDash());
        employee.setTodaysFollowup(dto.getTodaysFollowup() != null && dto.getTodaysFollowup());
        employee.setOverdueFollowup(dto.getOverdueFollowup() != null && dto.getOverdueFollowup());
        employee.setBatchWiseFee(dto.getBatchWiseFee() != null && dto.getBatchWiseFee());
        employee.setPaymentLink(dto.getPaymentLink() != null && dto.getPaymentLink());
        employee.setTimeTable(dto.getTimeTable() != null && dto.getTimeTable());
        employee.setTimeTableAttendance(dto.getTimeTableAttendance() != null && dto.getTimeTableAttendance());
        employee.setSendAppMsg(dto.getSendAppMsg() != null && dto.getSendAppMsg());
        employee.setShareVideo(dto.getShareVideo() != null && dto.getShareVideo());
        employee.setLiveLecture(dto.getLiveLecture() != null && dto.getLiveLecture());
        employee.setOfflineExam(dto.getOfflineExam() != null && dto.getOfflineExam());
        employee.setStudyNote(dto.getStudyNote() != null && dto.getStudyNote());
    }

    private void createUserCredentials(Employee employee, EmployeeRequestDTO requestDTO) {
        log.info("[AUTH] Creating user credentials for: {}", requestDTO.getUsername());

        User user = new User();
        user.setUsername(requestDTO.getUsername());
        user.setPassword(passwordEncoder.encode(requestDTO.getPassword()));
        user.setEmployee(employee);
        user.setRole(employee.getRole());
        user.setIsActive(true);
        user.setIsLocked(false);
        user.setFailedAttempts(0);

        userRepository.save(user);
        log.info(" User credentials created successfully");
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
                employee.getEmployeeName(),
                user.getUsername(),
                null // Password not available for resend
        );

        log.info("[EMAIL] Credentials email sent to: {}", employee.getEmailId());
    }

    private EmployeeResponseDTO convertToResponseDTO(Employee employee) {
        String photoUrl = null;
        if (employee.getPhoto() != null && !employee.getPhoto().isEmpty()) {
            photoUrl = baseUrl + "/api/files/employees/" + employee.getPhoto();
        }

        Boolean userActive = null;
        Boolean userLocked = null;
        Integer failedAttempts = null;
        User user = userRepository.findByEmployee(employee).orElse(null);
        if (user != null) {
            userActive = user.getIsActive();
            userLocked = user.getIsLocked();
            failedAttempts = user.getFailedAttempts();
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
                .userActive(userActive)
                .userLocked(userLocked)
                .failedAttempts(failedAttempts)
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

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getEmployeePermissions(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        List<EmployeeMenuPermission> permissions =
                employeeMenuPermissionRepository.findByEmployee(employee);

        return permissions.stream()
                .map(perm -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("menuId", perm.getMenu().getId());
                    map.put("hasAccess", perm.getHasAccess());
                    return map;
                })
                .collect(Collectors.toList());
    }

    /**
     * Get current logged-in user's employee profile
     */
    @Transactional(readOnly = true)
    public EmployeeResponseDTO getCurrentUserProfile() {
        try {
            User currentUser = getCurrentUser();
            Employee employee = currentUser.getEmployee();

            if (employee == null) {
                throw new ResourceNotFoundException("Employee profile not found for current user");
            }

            log.info("[INFO] Fetching profile for user: {}", currentUser.getUsername());
            return convertToResponseDTO(employee);

        } catch (Exception e) {
            log.error("[FAIL] Error fetching current user profile: {}", e.getMessage());
            throw new RuntimeException("Failed to load profile: " + e.getMessage());
        }
    }

    /**
     * Get username for an employee (password is NEVER returned)
     */
    @Transactional(readOnly = true)
    public Map<String, String> getUserCredentials(Long employeeId) {
        Map<String, String> result = new HashMap<>();

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        userRepository.findByEmployee(employee).ifPresent(user -> {
            result.put("username", user.getUsername());
            result.put("hasCredentials", "true");
            // NEVER return password - security best practice
        });

        return result;
    }
}