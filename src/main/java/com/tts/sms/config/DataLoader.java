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
    private final EmailTemplateRepository emailTemplateRepository;

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
        loadDefaultEmailTemplates();
    }

    private void loadRoles() {
        List<String> roleNames = Arrays.asList("SUPER_ADMIN", "ADMIN", "COUNSELOR", "TRAINER");
        int count = 0;

        for (String name : roleNames) {
            if (!roleRepository.existsByRoleTitle(name)) {
                Role role = new Role();
                role.setRoleTitle(name);
                role.setIsActive(true);
                roleRepository.save(role);
                count++;
            }
        }

        if (count > 0) {
            log.info(" Loaded {} roles", count);
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
                    createMenu("Reports", "Fees Collection", "/reports/fees-collection", 15),

                    // EMAIL CAMPAIGN
                    createMenu("Email Campaign", "Email Campaign", "/email-campaign", 16));

            menuRepository.saveAll(menus);
            log.info(" Loaded {} menus", menus.size());
        }

        // Ensure Email Campaign menu exists (for existing databases)
        Menu emailCampaignMenu = menuRepository.findByMenuUrl("/email-campaign")
                .orElseGet(() -> {
                    Menu menu = createMenu("Email Campaign", "Email Campaign", "/email-campaign", 16);
                    Menu saved = menuRepository.save(menu);
                    log.info(" Loaded Email Campaign menu (ID: {})", saved.getId());
                    return saved;
                });

        // Grant Email Campaign module access to all roles (does NOT touch any other menu permissions)
        List<Role> allRoles = roleRepository.findAll();
        for (Role role : allRoles) {
            if (roleMenuPermissionRepository.findByRoleAndMenu(role, emailCampaignMenu).isEmpty()) {
                RoleMenuPermission permission = new RoleMenuPermission();
                permission.setRole(role);
                permission.setMenu(emailCampaignMenu);
                permission.setHasAccess(true);
                roleMenuPermissionRepository.save(permission);
                log.info(" Granted Email Campaign module access to role: {}", role.getRoleTitle());
            }
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
                if (roleMenuPermissionRepository.findByRoleAndMenu(superAdminRole, menu).isEmpty()) {
                    RoleMenuPermission permission = new RoleMenuPermission();
                    permission.setRole(superAdminRole);
                    permission.setMenu(menu);
                    permission.setHasAccess(true);
                    roleMenuPermissionRepository.save(permission);
                }
            }

            log.info(" Granted full menu access to SUPER_ADMIN role");

            // Create Super Admin Employee
            Employee savedEmployee = employeeRepository.findByEmailId(superAdminEmail).orElseGet(() -> {
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

                return employeeRepository.save(superAdmin);
            });

            log.info(" Super Admin employee initialized: {}", savedEmployee.getEmployeeName());

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
                        superAdminPassword);
                log.info(" Credentials email sent to: {}", superAdminEmail);
            } catch (Exception e) {
                log.warn("[WARN] Failed to send credentials email: {}", e.getMessage());
            }

            log.info("=======================================================");
            log.info(" SUPER ADMIN USER CREATED SUCCESSFULLY!");
            log.info("=======================================================");
            log.info("[EMAIL] Email: {}", superAdminEmail);
            log.info("[USER] Username: {}", superAdminUsername);
            log.info("[KEY] Password: {}", superAdminPassword);
            log.info("[WARN] IMPORTANT: Change the password after first login!");
            log.info("=======================================================");

        } catch (Exception e) {
            log.error("[FAIL] Error creating super admin user: {}", e.getMessage(), e);
        }
    }

    private void loadDefaultEmailTemplates() {
        if (emailTemplateRepository.count() == 0) {
            EmailTemplate t1 = EmailTemplate.builder()
                    .templateName("AI/ML Engineer - Virtual Interview")
                    .emailSubject("Exclusive Career Opportunity: AI/ML Engineer Position at TechnoKraft")
                    .hiringRole("AI/ML Engineer")
                    .opportunityType("JOB")
                    .conductMode("ONLINE")
                    .onlinePlatform("GOOGLE_MEET")
                    .meetingLink("https://meet.google.com/xyz-abcd-efg")
                    .eventDate("Upcoming Monday")
                    .eventTime("10:30 AM IST")
                    .customNotes("Round 1: Problem Solving & Python Assessment. Round 2: Machine Learning Architecture & Projects. Please have your IDE ready.")
                    .isActive(true)
                    .createdBy("SYSTEM")
                    .build();

            EmailTemplate t2 = EmailTemplate.builder()
                    .templateName("Full Stack Java Developer - Campus Drive")
                    .emailSubject("Invitation for Full Stack Java Developer Hiring Drive at TechnoKraft")
                    .hiringRole("Full Stack Java Developer")
                    .opportunityType("JOB")
                    .conductMode("OFFLINE")
                    .venueAddress("TechnoKraft Training & Solution Pvt. Ltd., 1st Floor, Kanchwala Avenue, College Road, Nashik 422005")
                    .eventDate("Upcoming Wednesday")
                    .eventTime("11:00 AM IST")
                    .customNotes("Round 1: Technical Assessment & Coding. Round 2: Architecture & Project Review. Please keep your portfolio and code repositories accessible.")
                    .isActive(true)
                    .createdBy("SYSTEM")
                    .build();

            emailTemplateRepository.saveAll(Arrays.asList(t1, t2));
            log.info(" Loaded {} default email templates", 2);
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