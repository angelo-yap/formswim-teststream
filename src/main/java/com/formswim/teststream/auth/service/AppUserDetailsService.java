package com.formswim.teststream.auth.service;

import com.formswim.teststream.auth.model.AppUser;
import com.formswim.teststream.auth.repository.UserRepository;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final LoginThrottleService loginThrottleService;

    public AppUserDetailsService(UserRepository userRepository, LoginThrottleService loginThrottleService) {
        this.userRepository = userRepository;
        this.loginThrottleService = loginThrottleService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String normalizedEmail = AppUser.normalizeEmail(username);

        if (loginThrottleService.isBlocked(normalizedEmail)) {
            throw new LockedException("Invalid email or password");
        }

        AppUser user = userRepository.findByEmailIgnoreCase(normalizedEmail)
            .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password"));

        if (!user.isEnabled()) {
            throw new DisabledException("Invalid email or password");
        }

        return User.withUsername(user.getEmail())
            .password(user.getPasswordHash())
            .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
            .disabled(!user.isEnabled())
            .build();
    }
}