package com.tts.sms.repository;

import com.tts.sms.model.Employee;
import com.tts.sms.model.EmployeeMenuPermission;
import com.tts.sms.model.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeMenuPermissionRepository extends JpaRepository<EmployeeMenuPermission, Long> {

    List<EmployeeMenuPermission> findByEmployee(Employee employee);

    Optional<EmployeeMenuPermission> findByEmployeeAndMenu(Employee employee, Menu menu);

    @Modifying
    @Transactional
    @Query("DELETE FROM EmployeeMenuPermission emp WHERE emp.employee.id = :employeeId")
    void deleteByEmployeeId(Long employeeId);
}