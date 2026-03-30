package com.formswim.teststream.workspace;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.formswim.teststream.auth.model.AppUser;
import com.formswim.teststream.auth.repository.UserRepository;
import com.formswim.teststream.shared.domain.TestCase;
import com.formswim.teststream.shared.domain.TestCaseRepository;
import com.formswim.teststream.shared.domain.TestStep;
import com.formswim.teststream.support.TestCaseFixtures;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkspaceTestCaseDetailsIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestCaseRepository testCaseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        testCaseRepository.deleteAll();
        userRepository.deleteAll();

        userRepository.save(new AppUser("team1.user@example.com", passwordEncoder.encode("Password123"), "TEAM1"));
        userRepository.save(new AppUser("team2.user@example.com", passwordEncoder.encode("Password123"), "TEAM2"));

        TestCase team1Case = TestCaseFixtures.detailedCase(
            "TEAM1",
            "TC-101",
            "Checkout flow summary",
            "Detailed checkout description",
            "User has valid cart",
            "Checkout/Payments"
        );
        team1Case.addStep(new TestStep(1, "Open checkout page", "Input cart", "Checkout page opens"));

        TestCase team2Case = TestCaseFixtures.basicCase("TEAM2", "TC-201", "OtherTeam/Auth");
        team2Case.addStep(new TestStep(1, "Open other team page", "", ""));

        testCaseRepository.saveAll(List.of(team1Case, team2Case));
    }

    @Test
    void unauthenticatedDetailsRouteRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/workspace/test-cases/TC-101"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
    }

    @Test
    void authenticatedInTeamCaseRendersDetailsWithExpectedModelAttributes() throws Exception {
        mockMvc.perform(get("/workspace/test-cases/TC-101")
                .with(user("team1.user@example.com").roles("USER")))
            .andExpect(status().isOk())
            .andExpect(view().name("test-case-details"))
            .andExpect(model().attribute("detailsCaseId", "TC-101"))
            .andExpect(model().attribute("detailsPageTitle", "Test case details"))
            .andExpect(model().attribute("detailsSummary", "Checkout flow summary"))
            .andExpect(model().attribute("detailsDescription", "Detailed checkout description"))
            .andExpect(model().attribute("detailsPrecondition", "User has valid cart"))
            .andExpect(model().attribute("detailsFolder", "Checkout/Payments"))
            .andExpect(model().attribute("detailsFolderSegments", List.of("Checkout", "Payments")))
            .andExpect(model().attribute("detailsStatus", "Draft"))
            .andExpect(model().attribute("detailsPriority", "High"))
            .andExpect(model().attribute("detailsComponents", "UI"))
            .andExpect(model().attribute("detailsTestCaseType", "Regression"))
            .andExpect(model().attribute("detailsLabels", "auth"))
            .andExpect(model().attribute("detailsSprint", "Sprint 1"))
            .andExpect(model().attribute("detailsFixVersions", "1.0"))
            .andExpect(model().attribute("detailsVersion", "V1"))
            .andExpect(model().attribute("detailsCreatedOn", "2026-03-01"))
            .andExpect(model().attribute("detailsUpdatedOn", "2026-03-02"))
            .andExpect(model().attribute("detailsStoryLinkages", "ST-1"))
            .andExpect(model().attribute("detailsSteps", hasSize(1)));
    }

    @Test
    void otherTeamCaseRedirectsBackToWorkspaceWithImportError() throws Exception {
        mockMvc.perform(get("/workspace/test-cases/TC-201")
                .with(user("team1.user@example.com").roles("USER")))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/workspace?importError=Test+case+not+found"));
    }
}