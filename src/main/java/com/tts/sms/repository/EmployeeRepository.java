package com.tts.sms.repository;

import com.tts.sms.model.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    boolean existsByEmailId(String emailId);

    boolean existsByEmailIdAndIdNot(String emailId, Long id);

    boolean existsByUsername(String username);

    boolean existsByUsernameAndIdNot(String username, Long id);

    @Query("SELECT e FROM Employee e LEFT JOIN FETCH e.role")
    Page<Employee> findAllWithRole(Pageable pageable);

    @Query("SELECT e FROM Employee e LEFT JOIN FETCH e.role WHERE " +
            "LOWER(e.employeeName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(e.emailId) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "e.mobileNumber LIKE CONCAT('%', :searchTerm, '%')")
    Page<Employee> searchEmployees(String searchTerm, Pageable pageable);

    @Query("SELECT e FROM Employee e WHERE " +
            "MONTH(e.dateOfBirth) = :month AND " +
            "DAY(e.dateOfBirth) = :day AND " +
            "e.isActive = true AND " +
            "e.emailId IS NOT NULL")
    List<Employee> findEmployeesWithBirthdayToday(@Param("month") int month, @Param("day") int day);

    @Query("SELECT e FROM Employee e WHERE " +
            "MONTH(e.dateOfBirth) = :month AND " +
            "DAY(e.dateOfBirth) BETWEEN :startDay AND :endDay AND " +
            "e.isActive = true AND " +
            "e.emailId IS NOT NULL")
    List<Employee> findEmployeesWithUpcomingBirthdays(
            @Param("month") int month,
            @Param("startDay") int startDay,
            @Param("endDay") int endDay
    );

    // NEW: Count employees by role
    @Query("SELECT COUNT(e) FROM Employee e WHERE e.role.roleTitle = :roleTitle")
    long countByRoleRoleTitle(@Param("roleTitle") String roleTitle);
}