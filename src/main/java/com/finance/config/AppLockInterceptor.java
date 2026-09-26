package com.finance.config;

import com.finance.model.entity.User;
import com.finance.repository.UserRepository;
import com.finance.service.AppLockService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AppLockInterceptor implements HandlerInterceptor {

    private final UserRepository userRepository;
    private final AppLockService appLockService;

    public AppLockInterceptor(UserRepository userRepository, AppLockService appLockService) {
        this.userRepository = userRepository;
        this.appLockService = appLockService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (request.getRequestURI().startsWith("/app-lock") || request.getRequestURI().endsWith("/logout")) {
            return true;
        }
        Authentication authentication = (Authentication) request.getUserPrincipal();
        if (authentication == null || !authentication.isAuthenticated()) {
            return true;
        }
        User user = userRepository.findByUsername(authentication.getName()).orElse(null);
        if (user == null || !appLockService.isEnabled(user)
                || appLockService.isUnlocked(user, request.getSession(false))) {
            return true;
        }
        response.sendRedirect(request.getContextPath() + "/app-lock");
        return false;
    }
}
