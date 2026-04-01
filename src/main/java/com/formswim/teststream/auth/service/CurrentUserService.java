package com.formswim.teststream.auth.service;


import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import com.formswim.teststream.auth.model.AppUser;
import com.formswim.teststream.auth.repository.UserRepository;

import jakarta.servlet.http.HttpSession;

@Service
public class CurrentUserService {
    private final UserRepository userRepository;  


    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
  
    public Optional<AppUser> resolveCurrentUser(HttpSession session, Authentication authentication) {
        String sessionEmail = (String) session.getAttribute("session_user");
        if (sessionEmail != null && !sessionEmail.isBlank()) {
            return userRepository.findByEmailIgnoreCase(sessionEmail.trim());
        }

        String principalName = resolveAuthenticatedPrincipalName(authentication);
        if (principalName == null) {
            return Optional.empty();
        }
        return userRepository.findByEmailIgnoreCase(principalName);
    }

    private String resolveAuthenticatedPrincipalName(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            String principalName = authentication.getName();
            if (principalName != null && !principalName.isBlank() && !"anonymousUser".equals(principalName)) {
                return principalName;
            }
        }
        return null;
    }
}
