// LoginController.java
package com.tts.sms.controller;

import com.tts.sms.dto.RoleResponseDTO;
import com.tts.sms.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class LoginController {

    private final RoleService roleService;

    @GetMapping("/login")
    public String loginPage(
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "logout", required = false) String logout,
            Model model) {

        // Fetch all active roles
        List<RoleResponseDTO> roles = roleService.getActiveRoles();
        model.addAttribute("roles", roles);

        if (error != null) {
            model.addAttribute("error", "Invalid username or password");
        }

        if (logout != null) {
            model.addAttribute("logoutMessage", "You have been logged out successfully");
        }

        return "auth/login";
    }
}