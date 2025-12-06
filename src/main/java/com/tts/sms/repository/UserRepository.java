package com.tts.sms.repository;

import com.tts.sms.model.Employee;
import com.tts.sms.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    Optional<User> findByEmployee(Employee employee);
    void deleteByEmployee(Employee employee);

    Optional<User> findByEmployee_EmailId(String email);

    @Query("SELECT u FROM User u WHERE u.username = :identifier OR u.employee.emailId = :identifier")
    Optional<User> findByUsernameOrEmail(@Param("identifier") String identifier);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE u.employee.emailId = :email")
    boolean existsByEmployeeEmail(@Param("email") String email);

}