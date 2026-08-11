package com.infosys.cfootprint.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class TemporaryPasswordInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();

        // Allow change-temp-password, login/logout, settings, public complaints, and avatar assets endpoints
        if (path.startsWith("/api/auth/") || 
            path.startsWith("/api/settings") || 
            path.startsWith("/api/support") ||
            path.startsWith("/api/users/avatar/")) {
            return true;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            if (userDetails.isTempPassword()) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Temporary password must be changed before accessing the platform.\"}");
                return false;
            }
        }

        return true;
    }
}
