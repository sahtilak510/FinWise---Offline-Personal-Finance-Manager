package com.finance.financeplus.web;

import com.finance.financeplus.service.FinancePlusSettingsService;
import com.finance.model.entity.User;
import com.finance.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class FinancePlusAccessInterceptor implements HandlerInterceptor {

    private static final String UNLOCKED_SESSION_PREFIX = "finance_plus_unlocked_";

    private final UserRepository userRepository;
    private final FinancePlusSettingsService settingsService;

    public FinancePlusAccessInterceptor(UserRepository userRepository,
                                        FinancePlusSettingsService settingsService) {
        this.userRepository = userRepository;
        this.settingsService = settingsService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            response.sendRedirect(request.getContextPath() + "/login");
            return false;
        }
        User user = userRepository.findByUsername(authentication.getName()).orElse(null);
        if (user == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return false;
        }
        HttpSession session = request.getSession(false);
        boolean unlocked = false;
        if (session != null) {
            Object value = session.getAttribute(UNLOCKED_SESSION_PREFIX + user.getId());
            if (value instanceof String state) {
                int separator = state.indexOf(':');
                if (separator > 0) {
                    String version = state.substring(0, separator);
                    long expiresAt = Long.parseLong(state.substring(separator + 1));
                    try {
                        unlocked = version.equals(settingsService.passcodeVersion(user))
                                && expiresAt > System.currentTimeMillis();
                    } catch (NumberFormatException exception) {
                        unlocked = false;
                    }
                }
            }
        }
        if (settingsService.isPasscodeEnabled(user) && !unlocked) {
            response.sendRedirect(request.getContextPath() + "/finance-plus/locked");
            return false;
        }
        return true;
    }
}
