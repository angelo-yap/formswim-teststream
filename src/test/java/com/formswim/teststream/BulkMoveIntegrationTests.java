package com.formswim.teststream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
import com.formswim.teststream.support.TestCaseFixtures;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BulkMoveIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestCaseRepository testCaseRepository;

    @BeforeEach
    void setUp() {
        testCaseRepository.deleteAll();
        userRepository.deleteAll();

        userRepository.save(new AppUser("team1.user@example.com", "test-hash", "TEAM1"));
        userRepository.save(new AppUser("team2.user@example.com", "test-hash", "TEAM2"));
        userRepository.save(new AppUser("noteam.user@example.com", "test-hash", "   "));

        testCaseRepository.saveAll(List.of(
            TestCaseFixtures.basicCase("TEAM1", "TC-101", "Payments/Core"),
            TestCaseFixtures.basicCase("TEAM1", "TC-102", "Payments/Core"),
            TestCaseFixtures.basicCase("TEAM2", "TC-201", "Auth/Login")
        ));
    }

    @Test
    void bulkMoveReturnsPartialSuccessAndMovesOnlyOwnedKeys() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workKeys", List.of("TC-101", "TC-201", "TC-999", "   ", "TC-101"));
        payload.put("targetFolder", "QA/NewFolder");

        mockMvc.perform(patch("/api/testcases/bulk-move")
                .with(csrf())
                .with(user("team1.user@example.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(payload)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.requestedCount").value(5))
            .andExpect(jsonPath("$.movedCount").value(1))
            .andExpect(jsonPath("$.invalidCount").value(1))
            .andExpect(jsonPath("$.forbiddenCount").value(1))
            .andExpect(jsonPath("$.notFoundCount").value(1))
            .andExpect(jsonPath("$.failures.length()").value(3));

        TestCase moved = testCaseRepository.findByTeamKeyAndWorkKey("TEAM1", "TC-101").orElseThrow();
        TestCase untouchedSameTeam = testCaseRepository.findByTeamKeyAndWorkKey("TEAM1", "TC-102").orElseThrow();
        TestCase untouchedOtherTeam = testCaseRepository.findByTeamKeyAndWorkKey("TEAM2", "TC-201").orElseThrow();

        assertThat(moved.getFolder()).isEqualTo("QA/NewFolder");
        assertThat(untouchedSameTeam.getFolder()).isEqualTo("Payments/Core");
        assertThat(untouchedOtherTeam.getFolder()).isEqualTo("Auth/Login");
    }

    @Test
    void bulkEndpointSupportsSingleWorkKeyMove() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workKeys", List.of("TC-102"));
        payload.put("targetFolder", "QA/Single");

        mockMvc.perform(patch("/api/testcases/bulk-move")
                .with(csrf())
                .with(user("team1.user@example.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(payload)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.requestedCount").value(1))
            .andExpect(jsonPath("$.movedCount").value(1))
            .andExpect(jsonPath("$.invalidCount").value(0))
            .andExpect(jsonPath("$.forbiddenCount").value(0))
            .andExpect(jsonPath("$.notFoundCount").value(0));

        TestCase moved = testCaseRepository.findByTeamKeyAndWorkKey("TEAM1", "TC-102").orElseThrow();
        assertThat(moved.getFolder()).isEqualTo("QA/Single");
    }

    @Test
    void bulkMoveReturnsUnauthorizedWhenPrincipalHasNoUserRecord() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workKeys", List.of("TC-101"));
        payload.put("targetFolder", "QA/NoUser");

        mockMvc.perform(patch("/api/testcases/bulk-move")
                .with(csrf())
                .with(user("ghost.user@example.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(payload)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void bulkMoveReturnsForbiddenWhenUserHasNoTeamKey() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workKeys", List.of("TC-101"));
        payload.put("targetFolder", "QA/Forbidden");

        mockMvc.perform(patch("/api/testcases/bulk-move")
                .with(csrf())
                .with(user("noteam.user@example.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(payload)))
            .andExpect(status().isForbidden());
    }

    @Test
    void bulkMoveReturnsBadRequestWhenTargetFolderIsBlankAfterTrim() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workKeys", List.of("TC-101"));
        payload.put("targetFolder", "   ");

        mockMvc.perform(patch("/api/testcases/bulk-move")
                .with(csrf())
                .with(user("team1.user@example.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(payload)))
            .andExpect(status().isBadRequest());
    }
}
