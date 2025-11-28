package com.tts.sms.repository;

import com.tts.sms.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByRoleTitle(String roleTitle);

    List<Role> findByIsActiveTrue();

    boolean existsByRoleTitle(String roleTitle);

    boolean existsByRoleTitleAndIdNot(String roleTitle, Long id);
}