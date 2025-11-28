package com.tts.sms.repository;

import com.tts.sms.model.Employee;
import com.tts.sms.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    Optional<User> findByEmployee(Employee employee);
    void deleteByEmployee(Employee employee);
}