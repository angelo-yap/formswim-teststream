package com.formswim.teststream.auth.controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @PostMapping("/login")
    public String login() {
        // TODO: Implement session/JWT login logic, validate credentials, and return appropriate response
        return "Login endpoint";
    }

    @PostMapping("/register")
    public String register() {
        // TODO: Implement user registration and save via UserRepository
        return "Register endpoint";
    }

    @GetMapping("/me")
    public String getCurrentUser() {
        // TODO: Return the currently authenticated user's details
        return "Current user mapping";
    }
}
