package com.tts.sms.config;

import com.tts.sms.model.User;
import com.tts.sms.model.Menu;
import com.tts.sms.repository.MenuRepository;
import com.tts.sms.repository.EmployeeMenuPermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SecurityInterceptor implements HandlerInterceptor {

    private final MenuRepository menuRepository;
    private final EmployeeMenuPermissionRepository employeeMenuPermissionRepository;

    private static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";

    // URLs that don't require permission check
    private static final List<String> EXCLUDED_PATHS = List.of(
            "/login", "/logout", "/error", "/api/", "/assets/", "/webjars/",
            "/css/", "/js/", "/images/", "/favicon.ico"
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String requestURI = request.getRequestURI();

        // Skip check for excluded paths
        if (EXCLUDED_PATHS.stream().anyMatch(requestURI::startsWith)) {
            return true;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() ||
                authentication.getPrincipal().equals("anonymousUser")) {
            log.warn("[WARN] Unauthenticated access attempt to: {}", requestURI);
            response.sendRedirect("/login?error=unauthorized");
            return false;
        }

        User currentUser = (User) authentication.getPrincipal();

        // SUPER_ADMIN has access to everything
        if (isSuperAdmin(currentUser)) {
            return true;
        }

        // Check if user has permission for this URL
        boolean hasPermission = checkUrlPermission(currentUser, requestURI);

        if (!hasPermission) {
            log.warn("[BLOCKED] SECURITY ALERT: User {} attempted unauthorized access to: {}",
                    currentUser.getUsername(), requestURI);

            // Return JSON for AJAX requests
            if (isAjaxRequest(request)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write(
                        "{\"error\":\"Access Denied\",\"message\":\"You do not have permission to access this resource. This incident has been logged.\"}"
                );
            } else {
                // Redirect to access denied page for regular requests
                request.getSession().setAttribute("accessDeniedMessage",
                        "You do not have permission to access this page. This incident has been logged.");
                response.sendRedirect("/access-denied");
            }
            return false;
        }

        return true;
    }

    private boolean checkUrlPermission(User user, String requestURI) {
        // Find menu matching the URL
        List<Menu> allMenus = menuRepository.findByIsActiveTrueOrderByMenuOrderAsc();

        for (Menu menu : allMenus) {
            if (menu.getMenuUrl() != null && requestURI.contains(menu.getMenuUrl())) {
                // Check if user has permission for this menu
                return employeeMenuPermissionRepository
                        .findByEmployeeAndMenu(user.getEmployee(), menu)
                        .map(permission -> permission.getHasAccess())
                        .orElse(false);
            }
        }

        // If no specific menu found, allow access (might be a sub-page)
        return true;
    }

    private boolean isSuperAdmin(User user) {
        return user.getRole() != null &&
                SUPER_ADMIN_ROLE.equalsIgnoreCase(user.getRole().getRoleTitle());
    }

    private boolean isAjaxRequest(HttpServletRequest request) {
        String requestedWith = request.getHeader("X-Requested-With");
        String accept = request.getHeader("Accept");
        return "XMLHttpRequest".equals(requestedWith) ||
                (accept != null && accept.contains("application/json"));
    }
}