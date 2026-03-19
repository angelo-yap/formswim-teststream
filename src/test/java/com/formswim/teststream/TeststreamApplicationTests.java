package com.formswim.teststream;

import com.formswim.teststream.auth.model.AppUser;
import com.formswim.teststream.auth.repository.UserRepository;
import com.formswim.teststream.etl.model.TestCase;
import com.formswim.teststream.etl.repository.TestCaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
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
				testCase("TEAM1", "TC-101", "Payments/Core"),
				testCase("TEAM1", "TC-102", "Payments/Core"),
				testCase("TEAM1", "TC-103", "Billing/Refunds"),
				testCase("TEAM2", "TC-201", "Auth/Login"),
				testCase("TEAM2", "TC-202", "Auth/Signup")
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
				testCase("TEAM1", "TC-301", " UI "),
				testCase("TEAM1", "TC-302", "UI"),
				testCase("TEAM1", "TC-303", "ui"),
				testCase("TEAM1", "TC-304", "Billing"),
				testCase("TEAM1", "TC-305", ""),
				testCase("TEAM1", "TC-306", "   "),
				testCase("TEAM1", "TC-307", null)
		));

		mockMvc.perform(get("/api/folders")
					.with(user("team1.user@example.com").roles("USER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(3)))
				.andExpect(jsonPath("$[0]").value("Billing"))
				.andExpect(jsonPath("$[1]").value("UI"))
				.andExpect(jsonPath("$[2]").value("ui"));
	}

	private TestCase testCase(String teamKey, String workKey, String folder) {
		return new TestCase(
				teamKey,
				workKey,
				"Summary " + workKey,
				"Description",
				"Precondition",
				"Draft",
				"Medium",
				"Assignee",
				"Reporter",
				"5m",
				"label",
				"component",
				"Sprint 1",
				"1.0",
				"V1",
				folder,
				"Regression",
				"creator@example.com",
				"2026-03-18",
				"editor@example.com",
				"2026-03-18",
				"ST-1",
				"No",
				"0"
		);
	}

}
