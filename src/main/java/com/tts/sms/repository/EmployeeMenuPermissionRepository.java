package com.tts.sms.repository;

import com.tts.sms.model.Employee;
import com.tts.sms.model.EmployeeMenuPermission;
import com.tts.sms.model.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeMenuPermissionRepository extends JpaRepository<EmployeeMenuPermission, Long> {

    List<EmployeeMenuPermission> findByEmployee(Employee employee);

    List<EmployeeMenuPermission> findByEmployeeAndHasAccessTrue(Employee employee);

    Optional<EmployeeMenuPermission> findByEmployeeAndMenu(Employee employee, Menu menu);

    @Modifying
    @Query("DELETE FROM EmployeeMenuPermission emp WHERE emp.employee.id = :employeeId")
    void deleteByEmployeeId(@Param("employeeId") Long employeeId);

    @Query("SELECT emp FROM EmployeeMenuPermission emp WHERE emp.employee.id = :employeeId")
    List<EmployeeMenuPermission> findByEmployeeId(@Param("employeeId") Long employeeId);
}