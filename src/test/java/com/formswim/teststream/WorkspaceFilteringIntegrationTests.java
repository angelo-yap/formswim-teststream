package com.formswim.teststream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.formswim.teststream.auth.model.AppUser;
import com.formswim.teststream.auth.repository.UserRepository;
import com.formswim.teststream.shared.domain.TestCase;
import com.formswim.teststream.shared.domain.TestCaseRepository;
import com.formswim.teststream.shared.domain.TestStep;
import com.formswim.teststream.support.TestCaseFixtures;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkspaceFilteringIntegrationTests {

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

        TestCase team1Login = TestCaseFixtures.basicCase("TEAM1", "TC-101", "Auth/Login");
        team1Login.setSummary("Login scenario");
        team1Login.setComponents("UI");
        team1Login.setStatus("Draft");
        team1Login.setTestCaseType("Smoke");
        team1Login.addStep(new TestStep(1, "Open login page", "", ""));

        TestCase team1Refund = TestCaseFixtures.basicCase("TEAM1", "TC-102", "Billing/Refunds");
        team1Refund.setSummary("Payment scenario");
        team1Refund.setComponents("API");
        team1Refund.setStatus("Pass");
        team1Refund.setTestCaseType("Regression");
        team1Refund.addStep(new TestStep(1, "Submit refund request", "", ""));

        TestCase team1Other = TestCaseFixtures.basicCase("TEAM1", "TC-103", "Reports");
        team1Other.setSummary("Analytics scenario");
        team1Other.setComponents("Backend");
        team1Other.setStatus("Draft");
        team1Other.setTestCaseType("Regression");
        team1Other.addStep(new TestStep(1, "Generate report", "", ""));

        TestCase team1NestedWindowsFolder = TestCaseFixtures.basicCase("TEAM1", "TC-104", "Auth//Login\\Mfa /");
        team1NestedWindowsFolder.setSummary("MFA scenario");
        team1NestedWindowsFolder.setComponents("Backend");
        team1NestedWindowsFolder.setStatus("Blocked");
        team1NestedWindowsFolder.setTestCaseType("Exploratory");
        team1NestedWindowsFolder.addStep(new TestStep(1, "Complete MFA", "", ""));

        TestCase team2Login = TestCaseFixtures.basicCase("TEAM2", "TC-201", "Auth/Login");
        team2Login.setSummary("Other team login");
        team2Login.setComponents("UI");
        team2Login.setStatus("Draft");
        team2Login.addStep(new TestStep(1, "Open login page", "", ""));

        testCaseRepository.saveAll(List.of(team1Login, team1Refund, team1Other, team1NestedWindowsFolder, team2Login));
    }

    @Test
    void workspaceSearchMatchesFolderInDatabase() throws Exception {
        MvcResult result = mockMvc.perform(get("/workspace")
                .with(user("team1.user@example.com").roles("USER"))
                .param("search", "billing/refunds"))
            .andExpect(status().isOk())
            .andExpect(model().attributeExists("testCases"))
            .andReturn();

        List<String> workKeys = extractWorkKeys(result);
        assertThat(workKeys).containsExactly("TC-102");
    }

    @Test
    void workspaceSearchMatchesStepSummaryInDatabase() throws Exception {
        MvcResult result = mockMvc.perform(get("/workspace")
                .with(user("team1.user@example.com").roles("USER"))
                .param("search", "refund request"))
            .andExpect(status().isOk())
            .andExpect(model().attributeExists("testCases"))
            .andReturn();

        List<String> workKeys = extractWorkKeys(result);
        assertThat(workKeys).containsExactly("TC-102");
    }

    @Test
    void workspaceAppliesStatusAndComponentFiltersInDatabase() throws Exception {
        MvcResult result = mockMvc.perform(get("/workspace")
                .with(user("team1.user@example.com").roles("USER"))
                .param("status", "draft")
                .param("component", "ui"))
            .andExpect(status().isOk())
            .andExpect(model().attributeExists("testCases"))
            .andReturn();

        List<String> workKeys = extractWorkKeys(result);
        assertThat(workKeys).containsExactly("TC-101");
    }

    @Test
    void workspaceSearchKeepsTeamIsolation() throws Exception {
        MvcResult result = mockMvc.perform(get("/workspace")
                .with(user("team1.user@example.com").roles("USER"))
                .param("search", "open login page"))
            .andExpect(status().isOk())
            .andExpect(model().attributeExists("testCases"))
            .andReturn();

        List<String> workKeys = extractWorkKeys(result);
        assertThat(workKeys).containsExactly("TC-101");
    }

    @Test
    void workspaceSearchMatchesTagTextFromTestCaseType() throws Exception {
        MvcResult result = mockMvc.perform(get("/workspace")
                .with(user("team1.user@example.com").roles("USER"))
                .param("search", "smoke"))
            .andExpect(status().isOk())
            .andExpect(model().attributeExists("testCases"))
            .andReturn();

        List<String> workKeys = extractWorkKeys(result);
        assertThat(workKeys).containsExactly("TC-101");
    }

    @Test
    void apiTestCasesFiltersByStepSummaryAndStatus() throws Exception {
        mockMvc.perform(get("/api/testcases")
                .with(user("team1.user@example.com").roles("USER"))
                .param("search", "refund request")
                .param("status", "pass"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].workKey").value("TC-102"))
            .andExpect(jsonPath("$[1]").doesNotExist());
    }

    @Test
    void apiTestCasesFiltersByTagFromTestCaseType() throws Exception {
        mockMvc.perform(get("/api/testcases")
                .with(user("team1.user@example.com").roles("USER"))
                .param("tag", "smoke"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].workKey").value("TC-101"))
            .andExpect(jsonPath("$[1]").doesNotExist());
    }

    @Test
    void apiTestCasesFolderFilterMatchesNestedWindowsStyleFolders() throws Exception {
        mockMvc.perform(get("/api/testcases")
                .with(user("team1.user@example.com").roles("USER"))
                .param("folder", "Auth/Login"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].workKey").value("TC-104"))
            .andExpect(jsonPath("$[1].workKey").value("TC-101"))
            .andExpect(jsonPath("$[2]").doesNotExist());
    }

    @SuppressWarnings("unchecked")
    private List<String> extractWorkKeys(MvcResult result) {
        List<TestCase> cases = (List<TestCase>) result.getModelAndView().getModel().get("testCases");
        return cases.stream().map(TestCase::getWorkKey).sorted().collect(Collectors.toList());
    }
}
