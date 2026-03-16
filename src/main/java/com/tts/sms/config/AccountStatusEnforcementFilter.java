package com.tts.sms.config;

import com.tts.sms.model.User;
import com.tts.sms.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountStatusEnforcementFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            filterChain.doFilter(request, response);
            return;
        }

        String identifier = authentication.getName();
        User user = userRepository.findByUsernameOrEmail(identifier).orElse(null);
        if (user == null) {
            forceLogout(request);
            handleBlocked(request, response, "User not found");
            return;
        }

        boolean active = Boolean.TRUE.equals(user.getIsActive());
        boolean locked = Boolean.TRUE.equals(user.getIsLocked());

        if (!active || locked) {
            log.warn("Blocking request for user={} (active={}, locked={}) path={}", user.getUsername(), active, locked, request.getRequestURI());
            forceLogout(request);
            handleBlocked(request, response, locked ? "Account locked" : "Account deactivated");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void forceLogout(HttpServletRequest request) {
        try {
            SecurityContextHolder.clearContext();
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
        } catch (Exception e) {
            log.debug("Failed to invalidate session: {}", e.getMessage());
        }
    }

    private void handleBlocked(HttpServletRequest request, HttpServletResponse response, String message) throws IOException {
        String path = request.getRequestURI();

        if (path != null && path.startsWith("/api/")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"UNAUTHORIZED\",\"message\":\"" + escapeJson(message) + "\"}");
            return;
        }

        response.sendRedirect("/login?blocked");
    }

    private String escapeJson(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
