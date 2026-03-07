package com.formswim.teststream.auth.controller;

import com.formswim.teststream.auth.model.AppUser;
import com.formswim.teststream.auth.repository.UserRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

@Controller
@RequestMapping("/auth")  
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;


    // redirect /auth to login
    @GetMapping("/")
    public String root() {
        return "redirect:/auth/login";
    }


    // show login page
    @GetMapping("/login")
    public String getLogin(HttpSession session) {

        AppUser user = (AppUser) session.getAttribute("session_user");

        if (user == null) {
            return "login";
        }

        return "workspace";
    }


    // process login (hashed)
    @PostMapping("/login")
    public String login(@RequestParam String email, @RequestParam String password, HttpServletRequest request) {

        Optional<AppUser> userOptional = userRepository.findByEmail(email);

        if (userOptional.isEmpty()) {
            return "login";
        }

        AppUser user = userOptional.get();

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            return "login";
        }

        request.getSession().setAttribute("session_user", user);

        return "workspace";
    }


    // register user (hashed)
    @PostMapping("/register")
    public String register(@RequestParam String email, @RequestParam String password) {

        Optional<AppUser> existingUser = userRepository.findByEmail(email);

        if (existingUser.isPresent()) {
            return "login";
        }

        String hashedPassword = passwordEncoder.encode(password);

        AppUser user = new AppUser(email, hashedPassword, null);
        userRepository.save(user);

        return "login";
    }

    // return logged in user's email
    @GetMapping("/me")
    @ResponseBody
    public String getCurrentUser(HttpSession session) {

        AppUser user = (AppUser) session.getAttribute("session_user");

        if (user == null) {
            return "No user logged in";
        }

        return "Logged in user: " + user.getEmail();
    }


    // logout
    @GetMapping("/logout")
    public String logout(HttpServletRequest request) {
        request.getSession().invalidate();
        return "login";
    }
}