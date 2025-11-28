package com.tts.sms.repository;

import com.tts.sms.model.RoleMenuPermission;
import com.tts.sms.model.Role;
import com.tts.sms.model.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface RoleMenuPermissionRepository extends JpaRepository<RoleMenuPermission, Long> {

    List<RoleMenuPermission> findByRole(Role role);

    List<RoleMenuPermission> findByRoleAndHasAccessTrue(Role role);

    Optional<RoleMenuPermission> findByRoleAndMenu(Role role, Menu menu);

    void deleteByRole(Role role);

    @Query("SELECT rmp FROM RoleMenuPermission rmp WHERE rmp.role.id = :roleId")
    List<RoleMenuPermission> findByRoleId(@Param("roleId") Long roleId);
}