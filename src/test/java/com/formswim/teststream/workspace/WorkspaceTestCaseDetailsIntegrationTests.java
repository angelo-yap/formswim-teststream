package com.formswim.teststream.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    private ObjectMapper objectMapper;

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

        TestCase blankTeam1Case = TestCaseFixtures.basicCase("TEAM1", "TC-102", "");
        blankTeam1Case.setDescription("");
        blankTeam1Case.setPrecondition("");
        blankTeam1Case.setStoryLinkages("");
        blankTeam1Case.setStatus("");
        blankTeam1Case.setPriority("");
        blankTeam1Case.setComponents("");
        blankTeam1Case.setTestCaseType("");
        blankTeam1Case.setLabels("");
        blankTeam1Case.setSprint("");
        blankTeam1Case.setFixVersions("");
        blankTeam1Case.setVersion("");
        blankTeam1Case.setEstimatedTime("");

        TestCase team2Case = TestCaseFixtures.basicCase("TEAM2", "TC-201", "OtherTeam/Auth");
        team2Case.addStep(new TestStep(1, "Open other team page", "", ""));

        testCaseRepository.saveAll(List.of(team1Case, blankTeam1Case, team2Case));
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

    @Test
    void detailsPageRendersEditableEmptyFieldsInsteadOfHidingThem() throws Exception {
        mockMvc.perform(get("/workspace/test-cases/TC-102")
                .with(user("team1.user@example.com").roles("USER")))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-edit-field=\"status\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-field-display=\"status\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-edit-field=\"priority\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-field-display=\"priority\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-field-display=\"components\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-field-display=\"sprint\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-field-display=\"folder\"")));
    }

    @Test
    void singleCaseEditOverwritesFullFieldValue() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workKeys", List.of("TC-101"));
        payload.put("findText", "Checkout flow summary");
        payload.put("replaceText", "Updated checkout flow summary");
        payload.put("fields", List.of("summary"));

        mockMvc.perform(patch("/api/testcases/bulk-edit")
                .with(csrf())
                .with(user("team1.user@example.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(payload)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updatedCaseCount").value(1))
            .andExpect(jsonPath("$.totalReplacements").value(1));

        TestCase updated = testCaseRepository.findByTeamKeyAndWorkKey("TEAM1", "TC-101").orElseThrow();
        assertThat(updated.getSummary()).isEqualTo("Updated checkout flow summary");
        assertThat(updated.getDescription()).isEqualTo("Detailed checkout description");
        assertThat(updated.getPrecondition()).isEqualTo("User has valid cart");
        assertThat(updated.getUpdatedOn()).isNotNull();
        assertThat(updated.getUpdatedOn()).isNotEqualTo("2026-03-02");
        assertThat(updated.getUpdatedOn()).matches("\\d{4}-\\d{2}-\\d{2}");
    }

    @Test
    void singleCaseEditCrossTeamCaseIsReportedAsForbidden() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workKeys", List.of("TC-201"));
        payload.put("findText", "Summary TC-201");
        payload.put("replaceText", "Attempted overwrite");
        payload.put("fields", List.of("summary"));

        mockMvc.perform(patch("/api/testcases/bulk-edit")
                .with(csrf())
                .with(user("team1.user@example.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(payload)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.forbiddenCount").value(1))
            .andExpect(jsonPath("$.updatedCaseCount").value(0))
            .andExpect(jsonPath("$.totalReplacements").value(0));

        TestCase unchanged = testCaseRepository.findByTeamKeyAndWorkKey("TEAM2", "TC-201").orElseThrow();
        assertThat(unchanged.getSummary()).isEqualTo("Summary TC-201");
    }

    @Test
    void singleCaseEditRequiresAuthentication() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workKeys", List.of("TC-101"));
        payload.put("findText", "Checkout flow summary");
        payload.put("replaceText", "New value");
        payload.put("fields", List.of("summary"));

        mockMvc.perform(patch("/api/testcases/bulk-edit")
                .with(csrf())
                .with(user("unknown.user@example.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(payload)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void singleCaseEditOverwritesFolderValue() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workKeys", List.of("TC-101"));
        payload.put("findText", "Checkout/Payments");
        payload.put("replaceText", "Checkout/Payments/Refunds");
        payload.put("fields", List.of("folder"));

        mockMvc.perform(patch("/api/testcases/bulk-edit")
                .with(csrf())
                .with(user("team1.user@example.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(payload)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updatedCaseCount").value(1))
            .andExpect(jsonPath("$.totalReplacements").value(1));

        TestCase updated = testCaseRepository.findByTeamKeyAndWorkKey("TEAM1", "TC-101").orElseThrow();
        assertThat(updated.getFolder()).isEqualTo("Checkout/Payments/Refunds");
        assertThat(updated.getUpdatedOn()).matches("\\d{4}-\\d{2}-\\d{2}");
    }

    @Test
    void singleCaseEditFolderToRootLevelValue() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workKeys", List.of("TC-101"));
        payload.put("findText", "Checkout/Payments");
        payload.put("replaceText", "Checkout");
        payload.put("fields", List.of("folder"));

        mockMvc.perform(patch("/api/testcases/bulk-edit")
                .with(csrf())
                .with(user("team1.user@example.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(payload)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updatedCaseCount").value(1));

        TestCase updated = testCaseRepository.findByTeamKeyAndWorkKey("TEAM1", "TC-101").orElseThrow();
        assertThat(updated.getFolder()).isEqualTo("Checkout");
    }

    @Test
    void singleCaseEditFolderCrossTeamIsRejected() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workKeys", List.of("TC-201"));
        payload.put("findText", "OtherTeam/Auth");
        payload.put("replaceText", "Stolen/Folder");
        payload.put("fields", List.of("folder"));

        mockMvc.perform(patch("/api/testcases/bulk-edit")
                .with(csrf())
                .with(user("team1.user@example.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(payload)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.forbiddenCount").value(1))
            .andExpect(jsonPath("$.updatedCaseCount").value(0));

        TestCase unchanged = testCaseRepository.findByTeamKeyAndWorkKey("TEAM2", "TC-201").orElseThrow();
        assertThat(unchanged.getFolder()).isEqualTo("OtherTeam/Auth");
    }

    @Test
    void singleCaseEditOverwritesStepFieldValue() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workKeys", List.of("TC-101"));
        payload.put("findText", "Open checkout page");
        payload.put("replaceText", "Navigate to checkout");
        payload.put("fields", List.of("stepSummary"));

        mockMvc.perform(patch("/api/testcases/bulk-edit")
                .with(csrf())
                .with(user("team1.user@example.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(payload)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updatedStepCount").value(1))
            .andExpect(jsonPath("$.totalReplacements").value(1));

        TestCase updated = testCaseRepository.findAllWithStepsByTeamKeyAndWorkKeyIn("TEAM1", List.of("TC-101")).get(0);
        assertThat(updated.getSteps().get(0).getStepSummary()).isEqualTo("Navigate to checkout");
        assertThat(updated.getSummary()).isEqualTo("Checkout flow summary");
    }

    @Test
    void singleCaseEditCanSetBlankPriorityViaDirectAssignment() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workKeys", List.of("TC-102"));
        payload.put("fieldValues", Map.of("priority", "Critical"));

        mockMvc.perform(patch("/api/testcases/bulk-edit")
                .with(csrf())
                .with(user("team1.user@example.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(payload)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updatedCaseCount").value(1))
            .andExpect(jsonPath("$.totalReplacements").value(0));

        TestCase updated = testCaseRepository.findByTeamKeyAndWorkKey("TEAM1", "TC-102").orElseThrow();
        assertThat(updated.getPriority()).isEqualTo("Critical");
        assertThat(updated.getUpdatedOn()).matches("\\d{4}-\\d{2}-\\d{2}");
    }
}
