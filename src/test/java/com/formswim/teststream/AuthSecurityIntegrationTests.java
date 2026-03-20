package com.formswim.teststream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.containsString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.formswim.teststream.auth.model.AppUser;
import com.formswim.teststream.auth.repository.UserRepository;
import com.formswim.teststream.auth.service.LoginThrottleService;
import com.formswim.teststream.auth.service.TeamCodeThrottleService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthSecurityIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LoginThrottleService loginThrottleService;

    @Autowired
    private TeamCodeThrottleService teamCodeThrottleService;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        loginThrottleService.resetFailures("127.0.0.1");
        loginThrottleService.resetFailures("203.0.113.10");
        teamCodeThrottleService.resetFailures("127.0.0.1");
        teamCodeThrottleService.resetFailures("203.0.113.10");

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
            .andExpect(model().attribute("userEmail", "user@example.com"))
            .andExpect(model().attribute("teamKey", "TEAM1"));
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
        String attackerIp = "203.0.113.10";
        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/login")
                    .with(csrf())
                    .with(request -> {
                        request.setRemoteAddr(attackerIp);
                        return request;
                    })
                    .param("email", "user@example.com")
                    .param("password", "WrongPassword"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        }

        assertThat(loginThrottleService.isBlocked(attackerIp)).isTrue();
        mockMvc.perform(post("/login")
                .with(csrf())
                .with(request -> {
                    request.setRemoteAddr(attackerIp);
                    return request;
                })
                .param("email", "user@example.com")
                .param("password", "Password123"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"))
            .andExpect(flash().attribute("errorMessage", containsString("Too many attempts")));
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

    @Test
    void registerBlankTeamCodeGeneratesNewTeamKey() throws Exception {
        mockMvc.perform(post("/register")
                .with(csrf())
                .param("email", "newuser@example.com")
                .param("password", "Password123")
                .param("confirmPassword", "Password123")
                .param("teamCode", ""))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"))
            .andExpect(flash().attributeExists("successMessage"))
            .andExpect(flash().attributeExists("generatedTeamCode"));

        AppUser created = userRepository.findByEmailIgnoreCase("newuser@example.com").orElseThrow();
        assertThat(created.getTeamKey()).isNotBlank();
        assertThat(created.getTeamKey()).hasSizeGreaterThanOrEqualTo(16);

        mockMvc.perform(get("/workspace").with(user("newuser@example.com").roles("USER")))
            .andExpect(status().isOk());
    }

    @Test
    void registerUnknownTeamCodeShowsFieldError() throws Exception {
        mockMvc.perform(post("/register")
                .with(csrf())
                .param("email", "unknownteam@example.com")
                .param("password", "Password123")
                .param("confirmPassword", "Password123")
                .param("teamCode", "NOT_A_REAL_TEAM"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/register"))
            .andExpect(flash().attribute("errorMessage", "Please correct the highlighted fields."))
            .andExpect(flash().attribute("fieldErrors", hasEntry("teamCode", "Team code not found. Leave blank to create a new team.")));
    }

    @Test
    void registerWithExistingTeamCodeJoinsThatTeam() throws Exception {
        mockMvc.perform(post("/register")
                .with(csrf())
                .param("email", "joiner@example.com")
                .param("password", "Password123")
                .param("confirmPassword", "Password123")
                .param("teamCode", "team1"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));

        AppUser created = userRepository.findByEmailIgnoreCase("joiner@example.com").orElseThrow();
        assertThat(created.getTeamKey()).isEqualTo("TEAM1");
    }

    @Test
    void repeatedUnknownTeamCodeAttemptsBlockRegistrationFromSameIp() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/register")
                    .with(csrf())
                    .with(request -> {
                        request.setRemoteAddr("203.0.113.10");
                        return request;
                    })
                    .param("email", "ipblock" + attempt + "@example.com")
                    .param("password", "Password123")
                    .param("confirmPassword", "Password123")
                    .param("teamCode", "NOT_A_REAL_TEAM"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/register"))
                .andExpect(flash().attribute("errorMessage", "Please correct the highlighted fields."));
        }

        mockMvc.perform(post("/register")
                .with(csrf())
                .with(request -> {
                    request.setRemoteAddr("203.0.113.10");
                    return request;
                })
                .param("email", "blocked@example.com")
                .param("password", "Password123")
                .param("confirmPassword", "Password123")
                .param("teamCode", "NOT_A_REAL_TEAM"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/register"))
            .andExpect(flash().attribute("errorMessage", "Too many attempts. Please try again later."));
    }
}
