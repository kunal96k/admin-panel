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
            // Check if admin already exists
            if (userRepository.existsByUsername("admin")) {
                log.info("✅ Default admin user already exists");
                return;
            }

            // Create Admin Role if not exists
            Role adminRole = roleRepository.findByRoleTitle("ADMIN")
                    .orElseGet(() -> {
                        Role role = new Role();
                        role.setRoleTitle("ADMIN");
                        role.setIsActive(true);
                        return roleRepository.save(role);
                    });

            log.info("✅ Admin role created/found: {}", adminRole.getRoleTitle());

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
            log.info("✅ Admin employee created: {}", savedEmployee.getEmployeeName());

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

            log.info("═══════════════════════════════════════════════════════");
            log.info("✅ DEFAULT ADMIN USER CREATED SUCCESSFULLY!");
            log.info("═══════════════════════════════════════════════════════");
            log.info("📧 Email: admin@tts.net.in");
            log.info("👤 Username: admin");
            log.info("🔑 Password: admin@123");
            log.info("⚠️  IMPORTANT: Change the password after first login!");
            log.info("═══════════════════════════════════════════════════════");

        } catch (Exception e) {
            log.error("❌ Error creating default admin user: {}", e.getMessage(), e);
        }
    }
}