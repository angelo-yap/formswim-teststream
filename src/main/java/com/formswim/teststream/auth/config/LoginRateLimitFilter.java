package com.formswim.teststream.auth.config;

import java.io.IOException;
import java.time.Duration;

import org.springframework.web.filter.OncePerRequestFilter;

import com.formswim.teststream.auth.service.LoginThrottleService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

final class LoginRateLimitFilter extends OncePerRequestFilter {

    private final LoginThrottleService loginThrottleService;

    LoginRateLimitFilter(LoginThrottleService loginThrottleService) {
        this.loginThrottleService = loginThrottleService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!isLoginPost(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(request);
        loginThrottleService.recordAttempt(clientIp);

        if (loginThrottleService.isBlocked(clientIp)) {
            Duration remaining = loginThrottleService.getRemainingBlockDuration(clientIp);
            long minutes = Math.max(1, (remaining.toSeconds() + 59) / 60);

            FlashMessageSupport.addFlashMessage(
                request,
                response,
                "errorMessage",
                "Too many attempts. Please try again in " + minutes + " minutes.",
                "/login"
            );
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isLoginPost(HttpServletRequest request) {
        if (request == null) {
            return false;
        }

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }

        String contextPath = request.getContextPath();
        String requestUri = request.getRequestURI();
        if (requestUri == null) {
            return false;
        }

        String expected = (contextPath == null ? "" : contextPath) + "/login";
        return expected.equals(requestUri) || "/login".equals(request.getServletPath());
    }

    private String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String remote = request.getRemoteAddr();
        return remote == null ? "" : remote;
    }
}
