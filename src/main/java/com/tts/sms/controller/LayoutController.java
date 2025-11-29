package com.tts.sms.controller;

import com.tts.sms.model.Employee;
import com.tts.sms.model.EmployeeMenuPermission;
import com.tts.sms.model.RoleMenuPermission;
import com.tts.sms.model.User;
import com.tts.sms.repository.EmployeeMenuPermissionRepository;
import com.tts.sms.repository.RoleMenuPermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.*;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Controller
@Slf4j
public class LayoutController {

    private final RoleMenuPermissionRepository roleMenuPermissionRepository;
    private final EmployeeMenuPermissionRepository employeeMenuPermissionRepository;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("pageTitle", "Dashboard");
        model.addAttribute("activePage", "dashboard");
        return "dashboard/dashboard";
    }

    @ModelAttribute("currentUser")
    public User getCurrentUser(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            return (User) authentication.getPrincipal();
        }
        return null;
    }

    @ModelAttribute("userMenuPermissions")
    public List<Long> getUserMenuPermissions(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("⚠️ No authentication found");
            return Collections.emptyList();
        }

        try {
            User user = (User) authentication.getPrincipal();
            Employee employee = user.getEmployee();

            log.info("🔑 Loading permissions for user: {} (Role: {})",
                    user.getUsername(),
                    employee.getRole().getRoleTitle());

            Set<Long> menuIds = new HashSet<>();

            // 1. Get employee-specific permissions (highest priority)
            List<EmployeeMenuPermission> employeePermissions =
                    employeeMenuPermissionRepository.findByEmployee(employee);

            if (!employeePermissions.isEmpty()) {
                log.info("📋 Found {} employee-specific permissions", employeePermissions.size());
                employeePermissions.stream()
                        .filter(EmployeeMenuPermission::getHasAccess)
                        .forEach(perm -> menuIds.add(perm.getMenu().getId()));
            }

            // 2. Get role-based permissions (fallback if no employee-specific permissions)
            List<RoleMenuPermission> rolePermissions =
                    roleMenuPermissionRepository.findByRoleAndHasAccessTrue(employee.getRole());

            if (!rolePermissions.isEmpty()) {
                log.info("👥 Found {} role-based permissions", rolePermissions.size());
                rolePermissions.forEach(perm -> menuIds.add(perm.getMenu().getId()));
            }

            // Always include Dashboard (menu_id = 1) for all authenticated users
            menuIds.add(1L);

            List<Long> sortedMenuIds = new ArrayList<>(menuIds);
            Collections.sort(sortedMenuIds);

            log.info("✅ User has access to {} menus: {}", sortedMenuIds.size(), sortedMenuIds);

            return sortedMenuIds;

        } catch (Exception e) {
            log.error("❌ Error loading menu permissions: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @GetMapping("/students/enquiry")
    public String enquiry(Model model) {
        model.addAttribute("pageTitle", "Student Enquiry");
        model.addAttribute("activePage", "enquiry");
        return "student/enquiry";
    }

    @GetMapping("/students/admission")
    public String admission(Model model) {
        model.addAttribute("pageTitle", "Student Admission");
        model.addAttribute("activePage", "admission");
        return "student/admission";
    }

    @GetMapping("/accounts/fees-manager")
    public String feesManager(Model model) {
        model.addAttribute("pageTitle", "Fees Manager");
        model.addAttribute("activePage", "fees-manager");
        return "accounts/fees-manager";
    }

    @GetMapping("/printing/certificate")
    public String certificate(Model model) {
        model.addAttribute("pageTitle", "Certificate");
        model.addAttribute("activePage", "certificate");
        return "printing/certificate";
    }

    @GetMapping("/master/course")
    public String course(Model model) {
        model.addAttribute("pageTitle", "Course");
        model.addAttribute("activePage", "course");
        return "master/course";
    }

    @GetMapping("/master/batch")
    public String batch(Model model) {
        model.addAttribute("pageTitle", "Batch");
        model.addAttribute("activePage", "batch");
        return "master/batch";
    }

    @GetMapping("/master/employee")
    public String employee(Model model) {
        model.addAttribute("pageTitle", "Employee");
        model.addAttribute("activePage", "employee");
        return "master/employee";
    }

    @GetMapping("/master/role")
    public String role(Model model) {
        model.addAttribute("pageTitle", "Role Management");
        model.addAttribute("activePage", "role");
        return "master/role";
    }

    @GetMapping("/master/bank")
    public String bank(Model model) {
        model.addAttribute("pageTitle", "Bank Master");
        model.addAttribute("activePage", "bank");
        return "master/bank";
    }

    @GetMapping("/master/lead-source")
    public String leadSource(Model model) {
        model.addAttribute("pageTitle", "Lead Source");
        model.addAttribute("activePage", "lead-source");
        return "master/lead-source";
    }

    @GetMapping("/master/package")
    public String createPackage(Model model) {
        model.addAttribute("pageTitle", "Create Package");
        model.addAttribute("activePage", "create-package");
        return "master/package";
    }

    @GetMapping("/master/online-payment")
    public String onlinePayment(Model model) {
        model.addAttribute("pageTitle", "Online Payment Mode");
        model.addAttribute("activePage", "online-payment");
        return "master/payment-mode";
    }

    @GetMapping("/reports/course-wise-sales")
    public String courseWiseSales(Model model) {
        model.addAttribute("pageTitle", "Course-wise Sales Report");
        model.addAttribute("activePage", "course-wise-sales");
        return "reports/course-wise-sales";
    }

    @GetMapping("/reports/fees-collection")
    public String feesCollection(Model model) {
        model.addAttribute("pageTitle", "Fees Collection Report");
        model.addAttribute("activePage", "fees-collection");
        return "reports/fees-collection";
    }
}