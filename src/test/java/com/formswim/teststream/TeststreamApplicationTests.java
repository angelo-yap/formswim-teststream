package com.formswim.teststream;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.formswim.teststream.auth.model.AppUser;
import com.formswim.teststream.auth.repository.UserRepository;
import com.formswim.teststream.shared.domain.TestCaseRepository;
import com.formswim.teststream.support.TestCaseFixtures;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TeststreamApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TestCaseRepository testCaseRepository;

	@Autowired
	private UserRepository userRepository;

	@BeforeEach
	void setUp() {
		testCaseRepository.deleteAll();
		userRepository.deleteAll();

		userRepository.save(new AppUser("team1.user@example.com", "test-hash", "TEAM1"));
		userRepository.save(new AppUser("team2.user@example.com", "test-hash", "TEAM2"));

		testCaseRepository.saveAll(List.of(
				TestCaseFixtures.basicCase("TEAM1", "TC-101", "Payments/Core"),
				TestCaseFixtures.basicCase("TEAM1", "TC-102", "Payments/Core"),
				TestCaseFixtures.basicCase("TEAM1", "TC-103", "Billing/Refunds"),
				TestCaseFixtures.basicCase("TEAM2", "TC-201", "Auth/Login"),
				TestCaseFixtures.basicCase("TEAM2", "TC-202", "Auth/Signup")
		));
	}

	@Test
	void getFoldersReturnsUniqueFoldersForTeam1() throws Exception {
		mockMvc.perform(get("/api/folders")
					.with(user("team1.user@example.com").roles("USER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$", containsInAnyOrder("Payments/Core", "Billing/Refunds")));
	}

	@Test
	void getFoldersReturnsOnlyFoldersForAuthenticatedUsersTeam() throws Exception {
		mockMvc.perform(get("/api/folders")
					.with(user("team2.user@example.com").roles("USER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$", containsInAnyOrder("Auth/Login", "Auth/Signup")));
	}

	@Test
	void getFoldersReturnsCleanSortedCaseSensitiveWhitespaceNormalizedArray() throws Exception {
		testCaseRepository.deleteAll();
		testCaseRepository.saveAll(List.of(
				TestCaseFixtures.basicCase("TEAM1", "TC-301", " UI "),
				TestCaseFixtures.basicCase("TEAM1", "TC-302", "UI"),
				TestCaseFixtures.basicCase("TEAM1", "TC-303", "ui"),
				TestCaseFixtures.basicCase("TEAM1", "TC-304", "Billing"),
				TestCaseFixtures.basicCase("TEAM1", "TC-305", ""),
				TestCaseFixtures.basicCase("TEAM1", "TC-306", "   "),
				TestCaseFixtures.basicCase("TEAM1", "TC-307", null)
		));

		mockMvc.perform(get("/api/folders")
					.with(user("team1.user@example.com").roles("USER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(3)))
				.andExpect(jsonPath("$[0]").value("Billing"))
				.andExpect(jsonPath("$[1]").value("UI"))
				.andExpect(jsonPath("$[2]").value("ui"));
	}

	@Test
	void getFoldersNormalizesWindowsPathSeparatorsForSidebarTree() throws Exception {
		testCaseRepository.deleteAll();
		testCaseRepository.saveAll(List.of(
				TestCaseFixtures.basicCase("TEAM1", "TC-351", "Auth\\Login\\Mfa"),
				TestCaseFixtures.basicCase("TEAM1", "TC-352", "Auth/Login/Mfa")
		));

		mockMvc.perform(get("/api/folders")
					.with(user("team1.user@example.com").roles("USER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0]").value("Auth/Login/Mfa"));
	}

	@Test
	void getTagsReturnsCombinedSortedDistinctValuesForTeam() throws Exception {
		testCaseRepository.deleteAll();

		var caseOne = TestCaseFixtures.basicCase("TEAM1", "TC-401", "Folder/A");
		caseOne.setComponents("UI, API");
		caseOne.setTestCaseType("Manual");

		var caseTwo = TestCaseFixtures.basicCase("TEAM1", "TC-402", "Folder/B");
		caseTwo.setComponents(" Firmware ");
		caseTwo.setTestCaseType("Regression");

		var caseThree = TestCaseFixtures.basicCase("TEAM2", "TC-501", "Folder/C");
		caseThree.setComponents("Security");
		caseThree.setTestCaseType("Exploratory");

		testCaseRepository.saveAll(List.of(caseOne, caseTwo, caseThree));

		mockMvc.perform(get("/api/tags")
					.with(user("team1.user@example.com").roles("USER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(5)))
				.andExpect(jsonPath("$", containsInAnyOrder("API", "Firmware", "Manual", "Regression", "UI")));
	}

	@Test
	void getTagsReturnsOnlyAuthenticatedUsersTeamTags() throws Exception {
		testCaseRepository.deleteAll();

		var teamOneCase = TestCaseFixtures.basicCase("TEAM1", "TC-601", "Folder/A");
		teamOneCase.setComponents("UI");
		teamOneCase.setTestCaseType("Manual");

		var teamTwoCase = TestCaseFixtures.basicCase("TEAM2", "TC-701", "Folder/B");
		teamTwoCase.setComponents("Backend");
		teamTwoCase.setTestCaseType("Regression");

		testCaseRepository.saveAll(List.of(teamOneCase, teamTwoCase));

		mockMvc.perform(get("/api/tags")
					.with(user("team2.user@example.com").roles("USER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$", containsInAnyOrder("Backend", "Regression")));
	}

	@Test
	void getComponentsReturnsSplitDistinctTeamValues() throws Exception {
		testCaseRepository.deleteAll();

		var teamOneCase = TestCaseFixtures.basicCase("TEAM1", "TC-801", "Folder/A");
		teamOneCase.setComponents("UI, API,  Firmware ");

		var teamOneCaseTwo = TestCaseFixtures.basicCase("TEAM1", "TC-802", "Folder/B");
		teamOneCaseTwo.setComponents("API");

		var teamTwoCase = TestCaseFixtures.basicCase("TEAM2", "TC-901", "Folder/C");
		teamTwoCase.setComponents("Security");

		testCaseRepository.saveAll(List.of(teamOneCase, teamOneCaseTwo, teamTwoCase));

		mockMvc.perform(get("/api/components")
					.with(user("team1.user@example.com").roles("USER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(3)))
				.andExpect(jsonPath("$", containsInAnyOrder("API", "Firmware", "UI")));
	}

	@Test
	void getStatusesReturnsDistinctValuesForAuthenticatedUsersTeam() throws Exception {
		testCaseRepository.deleteAll();

		var teamOneCase = TestCaseFixtures.basicCase("TEAM1", "TC-1001", "Folder/A");
		teamOneCase.setStatus("Draft");

		var teamOneCaseTwo = TestCaseFixtures.basicCase("TEAM1", "TC-1002", "Folder/B");
		teamOneCaseTwo.setStatus("Pass");

		var teamTwoCase = TestCaseFixtures.basicCase("TEAM2", "TC-1003", "Folder/C");
		teamTwoCase.setStatus("Blocked");

		testCaseRepository.saveAll(List.of(teamOneCase, teamOneCaseTwo, teamTwoCase));

		mockMvc.perform(get("/api/statuses")
					.with(user("team1.user@example.com").roles("USER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$", containsInAnyOrder("Draft", "Pass")));
	}

}
