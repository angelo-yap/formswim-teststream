package com.formswim.teststream;

import com.formswim.teststream.auth.model.AppUser;
import com.formswim.teststream.auth.repository.UserRepository;
import com.formswim.teststream.auth.service.LoginThrottleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthSecurityIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LoginThrottleService loginThrottleService;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        loginThrottleService.resetFailures("user@example.com");
        loginThrottleService.resetFailures("disabled@example.com");
        loginThrottleService.resetFailures("missing@example.com");

        userRepository.save(new AppUser("user@example.com", passwordEncoder.encode("Password123"), "TEAM1"));

        AppUser disabledUser = new AppUser("disabled@example.com", passwordEncoder.encode("Password123"), "TEAM2");
        disabledUser.setEnabled(false);
        userRepository.save(disabledUser);
    }

    @Test
    void unauthenticatedWorkspaceRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/workspace"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
    }

    @Test
    void loginRequiresCsrfToken() throws Exception {
        mockMvc.perform(post("/login")
                .param("email", "user@example.com")
                .param("password", "Password123"))
            .andExpect(status().isForbidden());
    }

    @Test
    void loginAcceptsCaseInsensitiveEmailAndUsesAuthenticatedPrincipal() throws Exception {
        mockMvc.perform(post("/login")
                .with(csrf())
                .param("email", "USER@EXAMPLE.COM")
                .param("password", "Password123"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/workspace"))
            .andExpect(authenticated().withUsername("user@example.com"));

        mockMvc.perform(get("/workspace").with(user("user@example.com").roles("USER")))
            .andExpect(status().isOk())
            .andExpect(model().attribute("userEmail", "user@example.com"));
    }

    @Test
    void unknownUserAndWrongPasswordShareTheSameFailureMessage() throws Exception {
        mockMvc.perform(post("/login")
                .with(csrf())
                .param("email", "missing@example.com")
                .param("password", "Password123"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));

        mockMvc.perform(post("/login")
                .with(csrf())
                .param("email", "user@example.com")
                .param("password", "WrongPassword"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
    }

    @Test
    void disabledUsersCannotLogIn() throws Exception {
        mockMvc.perform(post("/login")
                .with(csrf())
                .param("email", "disabled@example.com")
                .param("password", "Password123"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
    }

    @Test
    void registerRejectsInvalidEmailWithoutLeakingMessagesIntoUrl() throws Exception {
        mockMvc.perform(post("/register")
                .with(csrf())
                .param("email", "not-an-email")
                .param("password", "Password123")
                .param("confirmPassword", "Password123")
                .param("teamCode", "TEAM1"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/register"))
            .andExpect(flash().attribute("errorMessage", "Please correct the highlighted fields."))
            .andExpect(flash().attributeExists("fieldErrors"));
    }

    @Test
    void logoutRequiresCsrfToken() throws Exception {
        mockMvc.perform(post("/logout").with(user("user@example.com").roles("USER")))
            .andExpect(status().isForbidden());
    }
    @Test
    void repeatedFailuresTemporarilyBlockSubsequentLogins() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/login")
                    .with(csrf())
                    .param("email", "user@example.com")
                    .param("password", "WrongPassword"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        }

        assertThat(loginThrottleService.isBlocked("user@example.com")).isTrue();
        mockMvc.perform(post("/login")
                .with(csrf())
                .param("email", "user@example.com")
                .param("password", "Password123"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
    }


    @Test
    void registerExistingEmailShowsError() throws Exception {
        MvcResult result = mockMvc.perform(post("/register")
                .with(csrf())
                .param("email", "user@example.com")
                .param("password", "Password123")
                .param("confirmPassword", "Password123")
                .param("teamCode", "TEAM1"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/register"))
            .andExpect(flash().attribute("errorMessage", "Please correct the highlighted fields."))
            .andReturn();

        mockMvc.perform(get("/register").flashAttrs(result.getFlashMap()))
            .andExpect(status().isOk())
            .andExpect(view().name("register"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Create your account")));
    }
}
