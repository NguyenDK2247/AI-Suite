package com.aisuite.config;

import com.aisuite.service.SessionService;
import jakarta.servlet.http.*;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;

public class AuthInterceptor implements HandlerInterceptor {

    private final SessionService sessionService;

    public AuthInterceptor(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler) throws Exception {
        String token = extractToken(request);
        int userId = sessionService.validateSession(token);

        if (userId == -1) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"ok\":false,\"error\":\"Not logged in.\"}");
            return false;
        }

        request.setAttribute("userId", userId);
        return true;
    }

    private String extractToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null)
            return null;
        for (Cookie c : cookies) {
            if ("session".equals(c.getName()))
                return c.getValue();
        }
        return null;
    }
}
