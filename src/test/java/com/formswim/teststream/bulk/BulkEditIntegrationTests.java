package com.formswim.teststream.bulk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.formswim.teststream.auth.model.AppUser;
import com.formswim.teststream.auth.repository.UserRepository;
import com.formswim.teststream.shared.domain.TestCase;
import com.formswim.teststream.shared.domain.TestCaseRepository;
import com.formswim.teststream.shared.domain.TestStep;
import com.formswim.teststream.support.TestCaseFixtures;
import com.formswim.teststream.workspace.repository.FolderRepository;

@SpringBootTest(properties = {
    "teststream.bulk-edit.enabled=true",
    "teststream.bulk-edit.max-work-keys=10"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BulkEditIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestCaseRepository testCaseRepository;

    @Autowired
    private FolderRepository folderRepository;

    @BeforeEach
    void setUp() {
        testCaseRepository.deleteAll();
        folderRepository.deleteAll();
        userRepository.deleteAll();

        userRepository.save(new AppUser("team1.user@example.com", "test-hash", "TEAM1"));
        userRepository.save(new AppUser("team2.user@example.com", "test-hash", "TEAM2"));
        userRepository.save(new AppUser("noteam.user@example.com", "test-hash", "   "));

        TestCase team1CaseA = TestCaseFixtures.basicCase("TEAM1", "TC-101", "Payments/Core");
        team1CaseA.setSummary("Please Click the button. Click once.");
        team1CaseA.setDescription("Description has Click but should stay unchanged when field is excluded.");
        team1CaseA.addStep(new TestStep(1, "Click submit", "No action", "Click confirmation appears"));
        team1CaseA.addStep(new TestStep(2, "Review summary", "No Click here", "Done"));

        TestCase team1CaseB = TestCaseFixtures.basicCase("TEAM1", "TC-102", "Payments/Core");
        team1CaseB.setSummary("Click in second case");
        team1CaseB.addStep(new TestStep(1, "Click next", "", ""));

        TestCase team2Case = TestCaseFixtures.basicCase("TEAM2", "TC-201", "Auth/Login");
        team2Case.setSummary("Click from another team");
        team2Case.addStep(new TestStep(1, "Click hidden", "", ""));

        testCaseRepository.saveAll(List.of(team1CaseA, team1CaseB, team2Case));
    }

    @Test
    void bulkEditReplacesExactTextOnSelectedFieldsOnly() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workKeys", List.of("TC-101", "TC-201", "TC-999", "   ", "TC-101"));
        payload.put("findText", "Click");
        payload.put("replaceText", "Tap");
        payload.put("fields", List.of("summary", "stepSummary"));

        mockMvc.perform(patch("/api/testcases/bulk-edit")
                .with(csrf())
                .with(user("team1.user@example.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(payload)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.requestedCount").value(5))
            .andExpect(jsonPath("$.candidateCount").value(1))
            .andExpect(jsonPath("$.updatedCaseCount").value(1))
            .andExpect(jsonPath("$.updatedStepCount").value(1))
            .andExpect(jsonPath("$.totalReplacements").value(3))
            .andExpect(jsonPath("$.invalidCount").value(1))
            .andExpect(jsonPath("$.forbiddenCount").value(1))
            .andExpect(jsonPath("$.notFoundCount").value(1))
            .andExpect(jsonPath("$.failures.length()").value(3));

        TestCase edited = testCaseRepository.findAllWithStepsByTeamKeyAndWorkKeyIn("TEAM1", List.of("TC-101")).get(0);
        TestCase untouchedSameTeam = testCaseRepository.findByTeamKeyAndWorkKey("TEAM1", "TC-102").orElseThrow();
        TestCase untouchedOtherTeam = testCaseRepository.findByTeamKeyAndWorkKey("TEAM2", "TC-201").orElseThrow();

        assertThat(edited.getSummary()).isEqualTo("Please Tap the button. Tap once.");
        assertThat(edited.getDescription()).isEqualTo("Description has Click but should stay unchanged when field is excluded.");
        assertThat(edited.getSteps().get(0).getStepSummary()).isEqualTo("Tap submit");
        assertThat(edited.getSteps().get(0).getExpectedResult()).isEqualTo("Click confirmation appears");

        assertThat(untouchedSameTeam.getSummary()).isEqualTo("Click in second case");
        assertThat(untouchedOtherTeam.getSummary()).isEqualTo("Click from another team");
    }

    @Test
    void bulkEditReturnsUnauthorizedWhenPrincipalHasNoUserRecord() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workKeys", List.of("TC-101"));
        payload.put("findText", "Click");
        payload.put("replaceText", "Tap");

        mockMvc.perform(patch("/api/testcases/bulk-edit")
                .with(csrf())
                .with(user("ghost.user@example.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(payload)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void bulkEditReturnsForbiddenWhenUserHasNoTeamKey() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workKeys", List.of("TC-101"));
        payload.put("findText", "Click");
        payload.put("replaceText", "Tap");

        mockMvc.perform(patch("/api/testcases/bulk-edit")
                .with(csrf())
                .with(user("noteam.user@example.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(payload)))
            .andExpect(status().isForbidden());
    }

    @Test
    void bulkEditReturnsBadRequestWhenFindTextIsBlankAfterTrim() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workKeys", List.of("TC-101"));
        payload.put("findText", "   ");
        payload.put("replaceText", "Tap");

        mockMvc.perform(patch("/api/testcases/bulk-edit")
                .with(csrf())
                .with(user("team1.user@example.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(payload)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void bulkEditReturnsBadRequestWhenWorkKeyCountExceedsLimit() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workKeys", List.of("K-1", "K-2", "K-3", "K-4", "K-5", "K-6", "K-7", "K-8", "K-9", "K-10", "K-11"));
        payload.put("findText", "Click");
        payload.put("replaceText", "Tap");

        mockMvc.perform(patch("/api/testcases/bulk-edit")
                .with(csrf())
                .with(user("team1.user@example.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(payload)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void bulkEditAcceptsSnakeCaseFieldAliases() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workKeys", List.of("TC-101"));
        payload.put("findText", "Click");
        payload.put("replaceText", "Tap");
        payload.put("fields", List.of("step_summary", "expected_result"));

        mockMvc.perform(patch("/api/testcases/bulk-edit")
                .with(csrf())
                .with(user("team1.user@example.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(payload)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updatedCaseCount").value(0))
            .andExpect(jsonPath("$.updatedStepCount").value(1))
            .andExpect(jsonPath("$.totalReplacements").value(2));

        TestCase edited = testCaseRepository.findAllWithStepsByTeamKeyAndWorkKeyIn("TEAM1", List.of("TC-101")).get(0);
        assertThat(edited.getSummary()).isEqualTo("Please Click the button. Click once.");
        assertThat(edited.getSteps().get(0).getStepSummary()).isEqualTo("Tap submit");
        assertThat(edited.getSteps().get(0).getExpectedResult()).isEqualTo("Tap confirmation appears");
    }

    @Test
    void bulkEditAcceptsEmptyReplacementTextForDeletion() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workKeys", List.of("TC-101"));
        payload.put("findText", "Click");
        payload.put("replaceText", "");
        payload.put("fields", List.of("summary"));

        mockMvc.perform(patch("/api/testcases/bulk-edit")
                .with(csrf())
                .with(user("team1.user@example.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(payload)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updatedCaseCount").value(1))
            .andExpect(jsonPath("$.updatedStepCount").value(0))
            .andExpect(jsonPath("$.totalReplacements").value(2));

        TestCase edited = testCaseRepository.findAllWithStepsByTeamKeyAndWorkKeyIn("TEAM1", List.of("TC-101")).get(0);
        assertThat(edited.getSummary()).isEqualTo("Please  the button.  once.");
    }

    @Test
    void bulkEditReturnsBadRequestForUnsupportedFieldAndDoesNotMutate() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workKeys", List.of("TC-101"));
        payload.put("findText", "Click");
        payload.put("replaceText", "Tap");
        payload.put("fields", List.of("step_instruction"));

        mockMvc.perform(patch("/api/testcases/bulk-edit")
                .with(csrf())
                .with(user("team1.user@example.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(payload)))
            .andExpect(status().isBadRequest());

        TestCase untouched = testCaseRepository.findAllWithStepsByTeamKeyAndWorkKeyIn("TEAM1", List.of("TC-101")).get(0);
        assertThat(untouched.getSummary()).isEqualTo("Please Click the button. Click once.");
        assertThat(untouched.getSteps().get(0).getStepSummary()).isEqualTo("Click submit");
        assertThat(untouched.getSteps().get(0).getExpectedResult()).isEqualTo("Click confirmation appears");
    }

    @Test
    void bulkEditRollsBackAllChangesWhenSaveFailsMidOperation() throws Exception {
        String oversizedReplacement = "X".repeat(300);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workKeys", List.of("TC-101", "TC-102"));
        payload.put("findText", "Assignee");
        payload.put("replaceText", oversizedReplacement);
        payload.put("fields", List.of("assignee"));

        mockMvc.perform(patch("/api/testcases/bulk-edit")
                .with(csrf())
                .with(user("team1.user@example.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(payload)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.requestedCount").value(2))
            .andExpect(jsonPath("$.invalidCount").value(2))
            .andExpect(jsonPath("$.failures[0].reason").value("CONFLICT"));

        TestCase first = testCaseRepository.findByTeamKeyAndWorkKey("TEAM1", "TC-101").orElseThrow();
        TestCase second = testCaseRepository.findByTeamKeyAndWorkKey("TEAM1", "TC-102").orElseThrow();

        assertThat(first.getAssignee()).isEqualTo("Assignee");
        assertThat(second.getAssignee()).isEqualTo("Assignee");
    }

    @Test
    void bulkEditFolderReplacementCreatesMissingFolderNodes() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workKeys", List.of("TC-101"));
        payload.put("findText", "Payments/Core");
        payload.put("replaceText", "QA/Edited");
        payload.put("fields", List.of("folder"));

        mockMvc.perform(patch("/api/testcases/bulk-edit")
                .with(csrf())
                .with(user("team1.user@example.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(payload)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updatedCaseCount").value(1));

        mockMvc.perform(get("/api/folders")
                .with(user("team1.user@example.com").roles("USER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0]").value("QA"))
            .andExpect(jsonPath("$[1]").value("QA/Edited"));
    }
}
