package com.tts.sms.config;

import com.tts.sms.model.Employee;
import com.tts.sms.model.Role;
import com.tts.sms.model.User;
import com.tts.sms.repository.EmployeeRepository;
import com.tts.sms.repository.RoleRepository;
import com.tts.sms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserDataLoader implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        try {
            // Check if ANY users already exist. We only auto-create if the database is empty.
            if (userRepository.count() > 0) {
                log.info("[INFO] Database already has users. Skipping default admin creation.");
                return;
            }

            log.info("[WARN] No users found in database. Initializing default superadmin...");

            // Create Admin Role if not exists
            Role adminRole = roleRepository.findByRoleTitle("ADMIN")
                    .orElseGet(() -> {
                        Role role = new Role();
                        role.setRoleTitle("ADMIN");
                        role.setIsActive(true);
                        return roleRepository.save(role);
                    });

            log.info("[OK] Admin role created/found: {}", adminRole.getRoleTitle());

            // Create Admin Employee
            Employee adminEmployee = new Employee();
            adminEmployee.setEmployeeName("System Administrator");
            adminEmployee.setMobileNumber("9999999999");
            adminEmployee.setEmailId("ktm.lover0123@gmail.com");
            adminEmployee.setDesignation("System Admin");
            adminEmployee.setGender("M");
            adminEmployee.setDateOfBirth(LocalDate.of(1990, 1, 1));
            adminEmployee.setAddress("TTS Institute, Nashik");
            adminEmployee.setRole(adminRole);
            adminEmployee.setIsActive(true);
            adminEmployee.setIsAdmin(true);

            Employee savedEmployee = employeeRepository.save(adminEmployee);
            log.info("[OK] Admin employee created: {}", savedEmployee.getEmployeeName());

            // Create Admin User with credentials
            User adminUser = new User();
            adminUser.setUsername("admin");
            adminUser.setPassword(passwordEncoder.encode("admin@123"));
            adminUser.setEmployee(savedEmployee);
            adminUser.setRole(adminRole);
            adminUser.setIsActive(true);
            adminUser.setIsLocked(false);
            adminUser.setFailedAttempts(0);

            userRepository.save(adminUser);

            log.info("=======================================================");
            log.info("[START] FIRST-TIME DATABASE INITIALIZATION COMPLETE!");
            log.info("[OK] DEFAULT SUPERADMIN CREATED SUCCESSFULLY!");
            log.info("=======================================================");
            log.info("[USER]  Username : admin");
            log.info("[KEY]  Password : admin@123");
            log.info("[EMAIL]  Email    : ktm.lover0123@gmail.com");
            log.info("[WARN] IMPORTANT: Change the password immediately!");
            log.info("=======================================================");

        } catch (Exception e) {
            log.error("[FAIL] Error creating default admin user: {}", e.getMessage(), e);
        }
    }
}