package com.tts.sms.controller;

import com.tts.sms.model.Employee;
import com.tts.sms.model.EmployeeMenuPermission;
import com.tts.sms.model.RoleMenuPermission;
import com.tts.sms.model.User;
import com.tts.sms.repository.EmployeeMenuPermissionRepository;
import com.tts.sms.repository.RoleMenuPermissionRepository;
import com.tts.sms.service.DashboardService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.*;

@RequiredArgsConstructor
@Controller
@Slf4j
public class LayoutController {

    private final RoleMenuPermissionRepository roleMenuPermissionRepository;
    private final EmployeeMenuPermissionRepository employeeMenuPermissionRepository;
    private final DashboardService dashboardService;

    // ========================================
    // MENU ID MAPPING
    // ========================================

    // Maps activePage key -> menu_id (matches sidebar data-menu-id attributes)
    private static final Map<String, Long> PAGE_MENU_ID_MAP = new HashMap<>();
    static {
        PAGE_MENU_ID_MAP.put("dashboard", 1L);
        PAGE_MENU_ID_MAP.put("enquiry", 2L);
        PAGE_MENU_ID_MAP.put("admission", 3L);
        PAGE_MENU_ID_MAP.put("fees-manager", 4L);
        PAGE_MENU_ID_MAP.put("certificate", 5L);
        PAGE_MENU_ID_MAP.put("course", 6L);
        PAGE_MENU_ID_MAP.put("batch", 7L);
        PAGE_MENU_ID_MAP.put("employee", 8L);
        PAGE_MENU_ID_MAP.put("role", 9L);
        PAGE_MENU_ID_MAP.put("bank", 10L);
        PAGE_MENU_ID_MAP.put("lead-source", 11L);
        PAGE_MENU_ID_MAP.put("create-package", 12L);
        PAGE_MENU_ID_MAP.put("online-payment", 13L);
        PAGE_MENU_ID_MAP.put("course-wise-sales", 14L);
        PAGE_MENU_ID_MAP.put("fees-collection", 15L);
    }

    /**
     * Common method to add base attributes for all pages
     */
    private void addBaseAttributes(Model model, String pageTitle, String activePage) {
        model.addAttribute("pageTitle", pageTitle);
        model.addAttribute("activePage", activePage);
        // Pass the menu ID for this page so layout can check permissions
        Long menuId = PAGE_MENU_ID_MAP.getOrDefault(activePage, 0L);
        model.addAttribute("currentMenuId", menuId);
        // Flag to trigger Bootstrap reinitialization
        model.addAttribute("requiresBootstrapInit", true);
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        addBaseAttributes(model, "Dashboard", "dashboard");
        Map<String, Object> stats = dashboardService.getDashboardStats();
        model.addAttribute("stats", stats);
        return "dashboard/dashboard";
    }

    @GetMapping("/access-denied")
    public String accessDenied(Model model, HttpSession session) {
        addBaseAttributes(model, "Access Denied", "access-denied");

        String customMessage = (String) session.getAttribute("accessDeniedMessage");
        if (customMessage != null) {
            model.addAttribute("customMessage", customMessage);
            session.removeAttribute("accessDeniedMessage");
        }
        return "auth/access-denied";
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

            // 1. First get Role-based permissions as a baseline
            List<RoleMenuPermission> rolePermissions = roleMenuPermissionRepository
                    .findByRoleAndHasAccessTrue(employee.getRole());

            if (!rolePermissions.isEmpty()) {
                log.info("👥 Found {} role-based permissions", rolePermissions.size());
                rolePermissions.forEach(perm -> menuIds.add(perm.getMenu().getId()));
            }

            // 2. Then apply Employee-specific permissions (these OVERRIDE role permissions)
            List<EmployeeMenuPermission> employeePermissions = employeeMenuPermissionRepository
                    .findByEmployee(employee);

            if (!employeePermissions.isEmpty()) {
                log.info("📋 Found {} employee-specific permissions", employeePermissions.size());
                for (EmployeeMenuPermission perm : employeePermissions) {
                    if (perm.getHasAccess()) {
                        // Explicitly granted to employee
                        menuIds.add(perm.getMenu().getId());
                    } else {
                        // Explicitly denied to employee (overrides role)
                        menuIds.remove(perm.getMenu().getId());
                    }
                }
            }

            // Note: Dashboard is NOT hardcoded. It is controlled by database permissions.

            List<Long> sortedMenuIds = new ArrayList<>(menuIds);
            Collections.sort(sortedMenuIds);

            log.info(" User has access to {} menus: {}", sortedMenuIds.size(), sortedMenuIds);
            return sortedMenuIds;

        } catch (Exception e) {
            log.error("❌ Error loading menu permissions: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    // ========================================
    // STUDENT MANAGEMENT
    // ========================================

    @GetMapping("/students/enquiry")
    public String enquiry(Model model) {
        addBaseAttributes(model, "Student Enquiry", "enquiry");
        return "student/enquiry";
    }

    @GetMapping("/students/admission")
    public String admission(Model model) {
        addBaseAttributes(model, "Student Admission", "admission");
        return "student/admission";
    }

    // ========================================
    // ACCOUNTS
    // ========================================

    @GetMapping("/accounts/fees-manager")
    public String feesManager(Model model) {
        addBaseAttributes(model, "Fees Manager", "fees-manager");
        return "accounts/fees-manager";
    }

    // ========================================
    // PRINTING
    // ========================================

    @GetMapping("/printing/certificate")
    public String certificate(Model model) {
        addBaseAttributes(model, "Certificate", "certificate");
        return "printing/certificate";
    }

    // ========================================
    // MASTER DATA
    // ========================================

    @GetMapping("/master/course")
    public String course(Model model) {
        addBaseAttributes(model, "Course Master", "course");
        return "master/course";
    }

    @GetMapping("/master/batch")
    public String batch(Model model) {
        addBaseAttributes(model, "Batch Master", "batch");
        return "master/batch";
    }

    @GetMapping("/master/employee")
    public String employee(Model model) {
        addBaseAttributes(model, "Employee Master", "employee");
        return "master/employee";
    }

    @GetMapping("/master/role")
    public String role(Model model) {
        addBaseAttributes(model, "Role Management", "role");
        return "master/role";
    }

    @GetMapping("/master/bank")
    public String bank(Model model) {
        addBaseAttributes(model, "Bank Master", "bank");
        return "master/bank";
    }

    @GetMapping("/master/lead-source")
    public String leadSource(Model model) {
        addBaseAttributes(model, "Lead Source", "lead-source");
        return "master/lead-source";
    }

    @GetMapping("/master/package")
    public String createPackage(Model model) {
        addBaseAttributes(model, "Create Package", "create-package");
        return "master/package";
    }

    @GetMapping("/master/online-payment")
    public String onlinePayment(Model model) {
        addBaseAttributes(model, "Online Payment Mode", "online-payment");
        return "master/payment-mode";
    }

    // ========================================
    // REPORTS
    // ========================================

    @GetMapping("/reports/course-sales-report")
    public String courseWiseSales(Model model) {
        addBaseAttributes(model, "Course-wise Sales", "course-wise-sales");
        return "reports/course-sales-report";
    }

    @GetMapping("/reports/fees-collection")
    public String feesCollection(Model model) {
        addBaseAttributes(model, "Fees Collection Report", "fees-collection");
        return "reports/fees-collection";
    }
}