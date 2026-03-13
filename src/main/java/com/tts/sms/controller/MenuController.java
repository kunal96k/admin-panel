    package com.tts.sms.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tts.sms.dto.MenuResponseDTO;
import com.tts.sms.dto.PermissionRequestDTO;
import com.tts.sms.service.RoleService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/menus")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MenuController {

    private final RoleService roleService;

    @GetMapping
    public ResponseEntity<List<MenuResponseDTO>> getMenusForRole(@RequestParam Long roleId) {
        return ResponseEntity.ok(roleService.getMenusForRole(roleId));
    }

    @PostMapping("/permissions")
    public ResponseEntity<Void> updatePermission(
            @RequestParam Long roleId,
            @Valid @RequestBody PermissionRequestDTO requestDTO) {
        roleService.updateMenuPermission(roleId, requestDTO);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/all")
    public ResponseEntity<List<MenuResponseDTO>> getAllMenus() {
        return ResponseEntity.ok(roleService.getAllMenus());
    }

    @GetMapping("/paged")
    public ResponseEntity<Page<MenuResponseDTO>> getMenusPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(roleService.getMenusPaginated(page, size));
    }
}