package com.tts.sms.controller;

import com.tts.sms.dto.*;
import com.tts.sms.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RoleController {

    private final RoleService roleService;

    /**
     * Get role permissions (for auto-loading in employee form)
     */
    @GetMapping("/{roleId}/permissions")
    public ResponseEntity<List<Map<String, Object>>> getRolePermissions(@PathVariable Long roleId) {
        List<Map<String, Object>> permissions = roleService.getRolePermissions(roleId);
        return ResponseEntity.ok(permissions);
    }

    @GetMapping
    public ResponseEntity<List<RoleResponseDTO>> getAllRoles() {
        return ResponseEntity.ok(roleService.getAllRoles());
    }

    @GetMapping("/paged")
    public ResponseEntity<Page<RoleResponseDTO>> getAllRolesPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(roleService.getRolesPaginated(page, size));
    }

    @GetMapping("/active")
    public ResponseEntity<List<RoleResponseDTO>> getActiveRoles() {
        return ResponseEntity.ok(roleService.getActiveRoles());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoleResponseDTO> getRoleById(@PathVariable Long id) {
        return ResponseEntity.ok(roleService.getRoleById(id));
    }

    @PostMapping
    public ResponseEntity<RoleResponseDTO> createRole(@Valid @RequestBody RoleRequestDTO requestDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roleService.createRole(requestDTO));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RoleResponseDTO> updateRole(
            @PathVariable Long id,
            @Valid @RequestBody RoleRequestDTO requestDTO) {
        return ResponseEntity.ok(roleService.updateRole(id, requestDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRole(@PathVariable Long id) {
        roleService.deleteRole(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{roleId}/permissions")
    public ResponseEntity<Void> updateMenuPermission(
            @PathVariable Long roleId,
            @Valid @RequestBody PermissionRequestDTO requestDTO) {
        roleService.updateMenuPermission(roleId, requestDTO);
        return ResponseEntity.ok().build();
    }
}