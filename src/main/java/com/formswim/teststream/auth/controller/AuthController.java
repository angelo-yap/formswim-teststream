package com.formswim.teststream.auth.controller;

import com.formswim.teststream.auth.dto.RegistrationForm;
import com.formswim.teststream.auth.model.AppUser;
import com.formswim.teststream.auth.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

@Controller
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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
                           RedirectAttributes redirectAttributes) {
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

        if (!fieldErrors.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please correct the highlighted fields.");
            redirectAttributes.addFlashAttribute("fieldErrors", fieldErrors);
            redirectAttributes.addFlashAttribute("email", registrationForm.getEmail());
            redirectAttributes.addFlashAttribute("teamCode", registrationForm.getTeamCode());
            return "redirect:/register";
        }

        String hashedPassword = passwordEncoder.encode(registrationForm.getPassword());
        AppUser user = new AppUser(normalizedEmail, hashedPassword, registrationForm.getTeamCode());
        userRepository.save(user);

        redirectAttributes.addFlashAttribute("successMessage", "Account created successfully. Sign in to continue.");
        return "redirect:/login";
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