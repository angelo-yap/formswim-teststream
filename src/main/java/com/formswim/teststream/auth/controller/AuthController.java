package com.formswim.teststream.auth.controller;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.formswim.teststream.auth.dto.RegistrationForm;
import com.formswim.teststream.auth.model.AppUser;
import com.formswim.teststream.auth.repository.UserRepository;
import com.formswim.teststream.auth.service.TeamCodeThrottleService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@Controller
public class AuthController {

    private static final int GENERATED_TEAM_CODE_LENGTH = 16;
    private static final String TEAM_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom TEAM_CODE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TeamCodeThrottleService teamCodeThrottleService;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, TeamCodeThrottleService teamCodeThrottleService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.teamCodeThrottleService = teamCodeThrottleService;
    }

    // Shows landing page
    @GetMapping("/")
    public String landing() {
        return "landing";
    }

    @GetMapping("/login")
    public String getLogin(Authentication authentication, HttpSession session, Model model) {
        if (isAuthenticated(authentication)) {
            return "redirect:/workspace";
        }

        moveSessionMessage(session, model, "errorMessage");
        moveSessionMessage(session, model, "successMessage");
        moveSessionMessage(session, model, "logoutMessage");

        return "login";
    }

    @GetMapping("/register")
    public String getRegister(Authentication authentication) {
        if (isAuthenticated(authentication)) {
            return "redirect:/workspace";
        }

        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute RegistrationForm registrationForm,
                           BindingResult bindingResult,
                           RedirectAttributes redirectAttributes,
                           HttpServletRequest request) {
        String normalizedEmail = AppUser.normalizeEmail(registrationForm.getEmail());
        Map<String, String> fieldErrors = new LinkedHashMap<>();

        if (bindingResult.hasErrors()) {
            bindingResult.getFieldErrors().forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        }

        if (!fieldErrors.containsKey("confirmPassword")
            && registrationForm.getPassword() != null
            && registrationForm.getConfirmPassword() != null
            && !registrationForm.getPassword().equals(registrationForm.getConfirmPassword())) {
            fieldErrors.put("confirmPassword", "Passwords do not match");
        }

        if (normalizedEmail != null && userRepository.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            fieldErrors.putIfAbsent("email", "An account with that email already exists");
        }

        String normalizedTeamCode = normalizeTeamCode(registrationForm.getTeamCode());
        boolean generatedTeam = false;
        if (normalizedTeamCode == null || normalizedTeamCode.isBlank()) {
            normalizedTeamCode = generateUniqueTeamCode();
            generatedTeam = true;
        } else {
            String clientIp = getClientIp(request);
            if (teamCodeThrottleService.isBlocked(clientIp)) {
                redirectAttributes.addFlashAttribute("errorMessage", "Too many attempts. Please try again later.");
                redirectAttributes.addFlashAttribute("email", registrationForm.getEmail());
                redirectAttributes.addFlashAttribute("teamCode", registrationForm.getTeamCode());
                return "redirect:/register";
            }

            // A team "exists" if any user is already assigned that team key.
            if (!userRepository.existsByTeamKeyIgnoreCase(normalizedTeamCode)) {
                fieldErrors.putIfAbsent("teamCode", "Team code not found. Leave blank to create a new team.");
                teamCodeThrottleService.recordFailure(clientIp);
            }
        }

        if (!fieldErrors.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please correct the highlighted fields.");
            redirectAttributes.addFlashAttribute("fieldErrors", fieldErrors);
            redirectAttributes.addFlashAttribute("email", registrationForm.getEmail());
            redirectAttributes.addFlashAttribute("teamCode", registrationForm.getTeamCode());
            return "redirect:/register";
        }

        String hashedPassword = passwordEncoder.encode(registrationForm.getPassword());
        AppUser user = new AppUser(normalizedEmail, hashedPassword, normalizedTeamCode);
        userRepository.save(user);

        if (generatedTeam) {
            redirectAttributes.addFlashAttribute("generatedTeamCode", normalizedTeamCode);
            redirectAttributes.addFlashAttribute("successMessage", "Account created successfully. Sign in to continue.");
        } else {
            redirectAttributes.addFlashAttribute("successMessage", "Account created successfully. Sign in to continue.");
        }
        return "redirect:/login";
    }

    private String normalizeTeamCode(String rawTeamCode) {
        if (rawTeamCode == null) {
            return null;
        }
        String trimmed = rawTeamCode.trim();
        if (trimmed.isBlank()) {
            return "";
        }
        return trimmed.toUpperCase();
    }

    private String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "";
        }

        // With server.forward-headers-strategy=framework, Spring will translate proxy headers
        // (Forwarded / X-Forwarded-For) so getRemoteAddr() reflects the real client when behind a proxy.
        String remote = request.getRemoteAddr();
        return remote == null ? "" : remote;
    }

    private String generateUniqueTeamCode() {
        for (int attempt = 0; attempt < 100; attempt++) {
            String candidate = generateTeamCode();
            if (!userRepository.existsByTeamKeyIgnoreCase(candidate)) {
                return candidate;
            }
        }
        // Extremely unlikely unless the code space is exhausted.
        throw new IllegalStateException("Unable to generate a unique team code");
    }

    private String generateTeamCode() {
        StringBuilder builder = new StringBuilder(GENERATED_TEAM_CODE_LENGTH);
        for (int i = 0; i < GENERATED_TEAM_CODE_LENGTH; i++) {
            int index = TEAM_CODE_RANDOM.nextInt(TEAM_CODE_ALPHABET.length());
            builder.append(TEAM_CODE_ALPHABET.charAt(index));
        }
        return builder.toString();
    }
    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null
            && authentication.isAuthenticated()
            && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private void moveSessionMessage(HttpSession session, Model model, String attributeName) {
        Object value = session.getAttribute(attributeName);
        if (value != null) {
            model.addAttribute(attributeName, value);
            session.removeAttribute(attributeName);
        }
    }
}