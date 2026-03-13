package com.tts.sms.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.tts.sms.model.Role;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByRoleTitle(String roleTitle);

    List<Role> findByIsActiveTrue();

    Page<Role> findByIsActiveTrue(Pageable pageable);

    boolean existsByRoleTitle(String roleTitle);

    boolean existsByRoleTitleAndIdNot(String roleTitle, Long id);
}