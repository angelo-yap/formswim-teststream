package com.formswim.teststream.auth.controller;

import com.formswim.teststream.auth.model.AppUser;
import com.formswim.teststream.auth.repository.UserRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

@Controller
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // Shows landing page
    @GetMapping("/")
    public String landing() {
        return "landing";
    }

    // Shows login page + displays any error/success/logout messages
    @GetMapping("/login")
    public String getLogin(HttpSession session,
                           Model model,
                           @RequestParam(required = false) String error,
                           @RequestParam(required = false) String success,
                           @RequestParam(required = false) String logout) {

        String sessionEmail = (String) session.getAttribute("session_user");

        if (sessionEmail != null) {
            return "redirect:/workspace";
        }

        if (error != null) model.addAttribute("errorMessage", error);
        if (success != null) model.addAttribute("successMessage", success);
        if (logout != null) model.addAttribute("logoutMessage", logout);

        return "login";
    }

    // Shows register page + displays messages if needed
    @GetMapping("/register")
    public String getRegister(HttpSession session,
                              Model model,
                              @RequestParam(required = false) String error,
                              @RequestParam(required = false) String success) {
        
        String sessionEmail = (String) session.getAttribute("session_user");
        if (sessionEmail != null) {
            return "redirect:/workspace";
        }

        if (error != null) model.addAttribute("errorMessage", error);
        if (success != null) model.addAttribute("successMessage", success);

        return "register";
    }

    // Processes login: checks email + password and creates session
    @PostMapping("/login")
    public String login(@RequestParam String email,
                        @RequestParam String password,
                        HttpServletRequest request) {

        Optional<AppUser> userOptional = userRepository.findByEmail(email);

        if (userOptional.isEmpty()) {
            return "redirect:/login?error=User not found";
        }

        AppUser user = userOptional.get();

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            return "redirect:/login?error=Invalid email or password";
        }

        request.getSession().setAttribute("session_user", user.getEmail());

        return "redirect:/workspace";
    }

    // Processes registration: creates new user with hashed password
    @PostMapping("/register")
    public String register(@RequestParam String email,
                           @RequestParam String password,
                           @RequestParam String confirmPassword,
                           @RequestParam(required = false, name = "teamCode") String teamCode) {
        
        if (email == null || email.isBlank()) {
            return "redirect:/register?error=Email is required";
        }

        if (password == null || password.isBlank()) {
            return "redirect:/register?error=Password is required";
        }

        if (password.length() < 8) {
            return "redirect:/register?error=Password must be at least 8 characters";
        }

        if (confirmPassword == null || confirmPassword.isBlank()) {
            return "redirect:/register?error=Please confirm your password";
        }

        if (!password.equals(confirmPassword)) {
            return "redirect:/register?error=Passwords do not match";
        }
        
        Optional<AppUser> existingUser = userRepository.findByEmail(email);

        if (existingUser.isPresent()) {
            return "redirect:/register?error=User already exists";
        }

        String hashedPassword = passwordEncoder.encode(password);

        AppUser user = new AppUser(email, hashedPassword, teamCode);
        userRepository.save(user);

        return "redirect:/login?success=Account created successfully";
    }

    // Shows workspace page (only if logged in)
    @GetMapping("/workspace")
    public String workspace(HttpSession session, Model model) {

        String sessionEmail = (String) session.getAttribute("session_user");

        if (sessionEmail == null) {
            return "redirect:/login?error=Please log in first";
        }

        model.addAttribute("userEmail", sessionEmail);

        return "workspace";
    }

    // Returns current logged-in user's email (for testing/debugging)
    @GetMapping("/me")
    @ResponseBody
    public String getCurrentUser(HttpSession session) {

        String sessionEmail = (String) session.getAttribute("session_user");

        if (sessionEmail == null) {
            return "No user logged in";
        }

        return "Logged in user: " + sessionEmail;
    }

    // Logs user out by destroying session
    @PostMapping("/logout")
    public String logout(HttpServletRequest request) {
        request.getSession().invalidate();
        return "redirect:/login?logout=You have been logged out";
    }
}