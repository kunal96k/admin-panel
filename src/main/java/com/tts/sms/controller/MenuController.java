    package com.tts.sms.controller;

    import com.tts.sms.dto.MenuResponseDTO;
    import com.tts.sms.dto.PermissionRequestDTO;
    import com.tts.sms.service.RoleService;
    import jakarta.validation.Valid;
    import lombok.RequiredArgsConstructor;
    import org.springframework.http.ResponseEntity;
    import org.springframework.web.bind.annotation.*;
    import java.util.List;

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
    }