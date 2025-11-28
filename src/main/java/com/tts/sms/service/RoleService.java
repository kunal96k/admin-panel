package com.tts.sms.service;

import com.tts.sms.dto.*;
import com.tts.sms.exception.DuplicateResourceException;
import com.tts.sms.exception.ResourceNotFoundException;
import com.tts.sms.model.*;
import com.tts.sms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoleService {

    private final RoleRepository roleRepository;
    private final MenuRepository menuRepository;
    private final RoleMenuPermissionRepository roleMenuPermissionRepository;

    @Transactional(readOnly = true)
    public List<RoleResponseDTO> getAllRoles() {
        return roleRepository.findAll().stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RoleResponseDTO> getActiveRoles() {
        return roleRepository.findByIsActiveTrue().stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RoleResponseDTO getRoleById(Long id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with id: " + id));
        return convertToResponseDTO(role);
    }

    @Transactional
    public RoleResponseDTO createRole(RoleRequestDTO requestDTO) {
        if (roleRepository.existsByRoleTitle(requestDTO.getRoleTitle())) {
            throw new DuplicateResourceException("Role already exists: " + requestDTO.getRoleTitle());
        }

        Role role = new Role();
        role.setRoleTitle(requestDTO.getRoleTitle());
        role.setIsActive(true);

        Role savedRole = roleRepository.save(role);
        log.info("Role created: {}", savedRole.getRoleTitle());
        return convertToResponseDTO(savedRole);
    }

    @Transactional
    public RoleResponseDTO updateRole(Long id, RoleRequestDTO requestDTO) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with id: " + id));

        if (roleRepository.existsByRoleTitleAndIdNot(requestDTO.getRoleTitle(), id)) {
            throw new DuplicateResourceException("Role already exists: " + requestDTO.getRoleTitle());
        }

        role.setRoleTitle(requestDTO.getRoleTitle());
        Role updatedRole = roleRepository.save(role);
        log.info("Role updated: {}", updatedRole.getRoleTitle());
        return convertToResponseDTO(updatedRole);
    }

    @Transactional
    public void deleteRole(Long id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with id: " + id));

        roleMenuPermissionRepository.deleteByRole(role);
        roleRepository.delete(role);
        log.info("Role deleted: {}", role.getRoleTitle());
    }

    @Transactional(readOnly = true)
    public List<MenuResponseDTO> getMenusForRole(Long roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with id: " + roleId));

        List<Menu> allMenus = menuRepository.findByIsActiveTrueOrderByMenuOrderAsc();

        return allMenus.stream().map(menu -> {
            boolean hasAccess = roleMenuPermissionRepository
                    .findByRoleAndMenu(role, menu)
                    .map(RoleMenuPermission::getHasAccess)
                    .orElse(false);

            return MenuResponseDTO.builder()
                    .id(menu.getId())
                    .mainMenu(menu.getMainMenu())
                    .submenu(menu.getSubmenu())
                    .menuUrl(menu.getMenuUrl())
                    .menuOrder(menu.getMenuOrder())
                    .hasAccess(hasAccess)
                    .build();
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MenuResponseDTO> getAllMenus() {
        return menuRepository.findByIsActiveTrueOrderByMenuOrderAsc().stream()
                .map(menu -> MenuResponseDTO.builder()
                        .id(menu.getId())
                        .mainMenu(menu.getMainMenu())
                        .submenu(menu.getSubmenu())
                        .menuUrl(menu.getMenuUrl())
                        .menuOrder(menu.getMenuOrder())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public void updateMenuPermission(Long roleId, PermissionRequestDTO requestDTO) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with id: " + roleId));

        Menu menu = menuRepository.findById(requestDTO.getMenuId())
                .orElseThrow(() -> new ResourceNotFoundException("Menu not found with id: " + requestDTO.getMenuId()));

        RoleMenuPermission permission = roleMenuPermissionRepository
                .findByRoleAndMenu(role, menu)
                .orElse(new RoleMenuPermission());

        permission.setRole(role);
        permission.setMenu(menu);
        permission.setHasAccess(requestDTO.getHasAccess());

        roleMenuPermissionRepository.save(permission);
        log.info("Permission updated for role: {} menu: {}", role.getRoleTitle(), menu.getSubmenu());
    }

    private RoleResponseDTO convertToResponseDTO(Role role) {
        return RoleResponseDTO.builder()
                .id(role.getId())
                .roleTitle(role.getRoleTitle())
                .isActive(role.getIsActive())
                .createdDate(role.getCreatedDate())
                .build();
    }
}