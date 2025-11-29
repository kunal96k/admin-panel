package com.tts.sms.config;

import com.tts.sms.model.*;
import com.tts.sms.repository.*;
import com.tts.sms.service.EmailTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataLoader implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final MenuRepository menuRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final RoleMenuPermissionRepository roleMenuPermissionRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailTemplateService emailTemplateService;

    @Value("${app.superadmin.email:kunalpatil192001@gmail.com}")
    private String superAdminEmail;

    @Value("${app.superadmin.username:admin}")
    private String superAdminUsername;

    @Value("${app.superadmin.password:admin@123}")
    private String superAdminPassword;

    @Override
    @Transactional
    public void run(String... args) {
        loadRoles();
        loadMenus();
        loadSuperAdmin();
    }

    private void loadRoles() {
        if (roleRepository.count() == 0) {
            List<String> roleNames = Arrays.asList("SUPER_ADMIN", "ADMIN", "COUNSELOR", "TRAINER");

            roleNames.forEach(name -> {
                Role role = new Role();
                role.setRoleTitle(name);
                role.setIsActive(true);
                roleRepository.save(role);
            });

            log.info(" Loaded {} roles", roleNames.size());
        }
    }

    private void loadMenus() {
        if (menuRepository.count() == 0) {
            List<Menu> menus = Arrays.asList(
                    createMenu("Dashboard", "Dashboard", "/dashboard", 1),

                    createMenu("Student", "Enquiry", "/students/enquiry", 2),
                    createMenu("Student", "Admission", "/students/admission", 3),

                    createMenu("Accounts", "Fees Manager", "/accounts/fees-manager", 4),

                    createMenu("Printing", "Certificate", "/printing/certificate", 5),

                    // MASTER SECTION
                    createMenu("Master", "Course", "/master/course", 6),
                    createMenu("Master", "Batch", "/master/batch", 7),  
                    createMenu("Master", "Employee", "/master/employee", 8),
                    createMenu("Master", "Role", "/master/role", 9),
                    createMenu("Master", "Bank", "/master/bank", 10),
                    createMenu("Master", "Lead Source", "/master/lead-source", 11),
                    createMenu("Master", "Create Package", "/master/package", 12),
                    createMenu("Master", "Online Payment", "/master/online-payment", 13),

                    // REPORTS
                    createMenu("Reports", "Course-wise Sales", "/reports/course-wise-sales", 14),
                    createMenu("Reports", "Fees Collection", "/reports/fees-collection", 15)
            );

            menuRepository.saveAll(menus);
            log.info(" Loaded {} menus", menus.size());
        }
    }

    private void loadSuperAdmin() {
        if (userRepository.existsByUsername(superAdminUsername)) {
            log.info(" Super Admin already exists");
            return;
        }

        try {
            // Get or create SUPER_ADMIN role
            Role superAdminRole = roleRepository.findByRoleTitle("SUPER_ADMIN")
                    .orElseGet(() -> {
                        Role role = new Role();
                        role.setRoleTitle("SUPER_ADMIN");
                        role.setIsActive(true);
                        return roleRepository.save(role);
                    });

            // Grant full access to all menus for SUPER_ADMIN role
            List<Menu> allMenus = menuRepository.findAll();
            for (Menu menu : allMenus) {
                RoleMenuPermission permission = new RoleMenuPermission();
                permission.setRole(superAdminRole);
                permission.setMenu(menu);
                permission.setHasAccess(true);
                roleMenuPermissionRepository.save(permission);
            }

            log.info(" Granted full menu access to SUPER_ADMIN role");

            // Create Super Admin Employee
            Employee superAdmin = new Employee();
            superAdmin.setEmployeeName("Super Administrator");
            superAdmin.setMobileNumber("9999999999");
            superAdmin.setEmailId(superAdminEmail);
            superAdmin.setDesignation("Super Admin");
            superAdmin.setGender("M");
            superAdmin.setDateOfBirth(LocalDate.of(1990, 1, 1));
            superAdmin.setAddress("TTS Institute, Nashik");
            superAdmin.setRole(superAdminRole);
            superAdmin.setIsActive(true);
            superAdmin.setIsAdmin(true);

            // Grant all mobile permissions
            superAdmin.setNewAdmission(true);
            superAdmin.setViewAdmission(true);
            superAdmin.setNewEnquiry(true);
            superAdmin.setViewEnquiry(true);
            superAdmin.setFeeManager(true);
            superAdmin.setManageCourse(true);
            superAdmin.setManageBatch(true);
            superAdmin.setAccDashboard(true);
            superAdmin.setCounsellorDash(true);
            superAdmin.setTodaysFollowup(true);
            superAdmin.setStudyNote(true);
            superAdmin.setPaymentLink(true);
            superAdmin.setTimeTable(true);
            superAdmin.setTimeTableAttendance(true);
            superAdmin.setOverdueFollowup(true);
            superAdmin.setBatchWiseFee(true);
            superAdmin.setSendAppMsg(true);
            superAdmin.setShareVideo(true);
            superAdmin.setLiveLecture(true);
            superAdmin.setOfflineExam(true);

            Employee savedEmployee = employeeRepository.save(superAdmin);
            log.info(" Super Admin employee created: {}", savedEmployee.getEmployeeName());

            // Create Super Admin User with credentials
            User superAdminUser = new User();
            superAdminUser.setUsername(superAdminUsername);
            superAdminUser.setPassword(passwordEncoder.encode(superAdminPassword));
            superAdminUser.setEmployee(savedEmployee);
            superAdminUser.setRole(superAdminRole);
            superAdminUser.setIsActive(true);
            superAdminUser.setIsLocked(false);
            superAdminUser.setFailedAttempts(0);

            userRepository.save(superAdminUser);

            // Send credentials email
            try {
                emailTemplateService.sendCredentialsEmail(
                        superAdminEmail,
                        "Super Administrator",
                        superAdminUsername,
                        superAdminPassword
                );
                log.info(" Credentials email sent to: {}", superAdminEmail);
            } catch (Exception e) {
                log.warn("⚠️ Failed to send credentials email: {}", e.getMessage());
            }

            log.info("═══════════════════════════════════════════════════════");
            log.info(" SUPER ADMIN USER CREATED SUCCESSFULLY!");
            log.info("═══════════════════════════════════════════════════════");
            log.info("📧 Email: {}", superAdminEmail);
            log.info("👤 Username: {}", superAdminUsername);
            log.info("🔑 Password: {}", superAdminPassword);
            log.info("⚠️  IMPORTANT: Change the password after first login!");
            log.info("═══════════════════════════════════════════════════════");

        } catch (Exception e) {
            log.error("❌ Error creating super admin user: {}", e.getMessage(), e);
        }
    }

    private Menu createMenu(String mainMenu, String submenu, String url, int order) {
        Menu menu = new Menu();
        menu.setMainMenu(mainMenu);
        menu.setSubmenu(submenu);
        menu.setMenuUrl(url);
        menu.setMenuOrder(order);
        menu.setIsActive(true);
        return menu;
    }
}