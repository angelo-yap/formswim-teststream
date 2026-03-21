package com.formswim.teststream.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

import com.formswim.teststream.auth.service.AppUserDetailsService;
import com.formswim.teststream.auth.service.LoginThrottleService;

import jakarta.servlet.http.HttpServletRequest;


@Configuration
public class SecurityConfig {

    private static final String GENERIC_LOGIN_ERROR = "Invalid email or password";
    private static final String CONTENT_SECURITY_POLICY =
        "default-src 'self'; " +
            "script-src 'self' 'unsafe-inline' https://cdn.tailwindcss.com; " +
            "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; " +
            "font-src 'self' https://fonts.gstatic.com data:; " +
            "img-src 'self' data:; " +
            "connect-src 'self'; " +
            "base-uri 'self'; " +
            "form-action 'self'; " +
            "frame-ancestors 'none'; " +
            "object-src 'none'";

    private final LoginThrottleService loginThrottleService;
    private final AppUserDetailsService appUserDetailsService;

    public SecurityConfig(LoginThrottleService loginThrottleService,
                          AppUserDetailsService appUserDetailsService) {
        this.loginThrottleService = loginThrottleService;
        this.appUserDetailsService = appUserDetailsService;
    }

    // Keeps password hashing working
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .userDetailsService(appUserDetailsService)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/login", "/register", "/error", "/test.html", "/workspace/test-cases/**").permitAll()
                .requestMatchers("/workspace/**", "/logout").authenticated()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("email")
                .successHandler(authenticationSuccessHandler())
                .failureHandler(authenticationFailureHandler())
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .logoutSuccessHandler(logoutSuccessHandler())
            )
            .sessionManagement(session -> session
                .sessionFixation(sessionFixation -> sessionFixation.migrateSession())
            )
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                .frameOptions(frameOptions -> frameOptions.deny())
                .contentTypeOptions(contentTypeOptions -> {})
                .referrerPolicy(referrerPolicy -> referrerPolicy.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
            );

        http.addFilterAfter(loginRateLimitFilter(), org.springframework.security.web.csrf.CsrfFilter.class);

        return http.build();
    }

    @Bean
    LoginRateLimitFilter loginRateLimitFilter() {
        return new LoginRateLimitFilter(loginThrottleService);
    }

    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> {
            loginThrottleService.resetFailures(getClientIp(request));
            response.sendRedirect(request.getContextPath() + "/workspace");
        };
    }

    @Bean
    public AuthenticationFailureHandler authenticationFailureHandler() {
        return (request, response, exception) -> {
            loginThrottleService.recordFailure(getClientIp(request));

            FlashMessageSupport.addFlashMessage(request, response, "errorMessage", GENERIC_LOGIN_ERROR, "/login");
            response.sendRedirect(request.getContextPath() + "/login");
        };
    }

    @Bean
    public LogoutSuccessHandler logoutSuccessHandler() {
        return (request, response, authentication) -> {
            FlashMessageSupport.addFlashMessage(request, response, "logoutMessage", "You have been logged out", "/login");
            response.sendRedirect(request.getContextPath() + "/login");
        };
    }

    private String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        // With server.forward-headers-strategy=framework, getRemoteAddr() reflects the real client when behind a proxy.
        String remote = request.getRemoteAddr();
        return remote == null ? "" : remote;
    }
}