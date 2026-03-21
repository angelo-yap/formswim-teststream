package com.formswim.teststream;

import java.io.ByteArrayInputStream;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.formswim.teststream.auth.model.AppUser;
import com.formswim.teststream.auth.repository.UserRepository;
import com.formswim.teststream.etl.model.TestCase;
import com.formswim.teststream.etl.model.TestStep;
import com.formswim.teststream.etl.repository.TestCaseRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkspaceExportIntegrationTests {

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

        userRepository.save(new AppUser("user@example.com", "$2a$10$WjTRvWwE2MJaPKU6fS5LMObD4FepNKe7J1fLGN9g2M8sOAxYQvNQe", "TEAM1"));
        userRepository.save(new AppUser("teamb@example.com", "$2a$10$WjTRvWwE2MJaPKU6fS5LMObD4FepNKe7J1fLGN9g2M8sOAxYQvNQe", "TEAM2"));

        TestCase first = new TestCase(
                "TEAM1",
                "TC-101",
                "Login works",
                "Login description",
                "User exists",
                "Draft",
                "High",
                "Alice",
                "Bob",
                "5m",
                "auth",
                "API",
                "Sprint 5",
                "1.0",
                "V1",
                "Auth/Login",
                "Regression",
                "creator@example.com",
                "2026-03-01",
                "editor@example.com",
                "2026-03-02",
                "ST-1",
                "No",
                "2"
        );
        first.addStep(new TestStep(1, "Open login page", "", "Login form is visible"));
        first.addStep(new TestStep(2, "Submit credentials", "user/pass", "Dashboard opens"));

        TestCase second = new TestCase(
            "TEAM2",
                "TC-202",
                "Reset password",
                "Reset description",
                "Mailbox reachable",
                "Pass",
                "Medium",
                "Cara",
                "Dan",
                "3m",
                "recovery",
                "UI",
                "Sprint 6",
                "1.1",
                "V2",
                "Auth/Recovery",
                "Smoke",
                "creator2@example.com",
                "2026-03-03",
                "editor2@example.com",
                "2026-03-04",
                "ST-2",
                "Yes",
                "1"
        );
        second.addStep(new TestStep(1, "Open forgot password", "", "Reset form is visible"));

        testCaseRepository.save(first);
        testCaseRepository.save(second);
    }

    @Test
    void exportEndpointReturnsXlsxForSelectedCasesOnly() throws Exception {
        MvcResult result = mockMvc.perform(get("/workspace/export")
                        .param("workKeys", "TC-101")
                        .with(user("user@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
            assertThat(workbook.getSheetAt(0).getLastRowNum()).isEqualTo(2);

            Row firstDataRow = workbook.getSheetAt(0).getRow(1);
            Row secondDataRow = workbook.getSheetAt(0).getRow(2);

            assertThat(firstDataRow.getCell(0).getStringCellValue()).isEqualTo("TC-101");
            assertThat(firstDataRow.getCell(13).getStringCellValue()).isEqualTo("Open login page");
            assertThat(secondDataRow.getCell(0).getStringCellValue()).isEmpty();
            assertThat(secondDataRow.getCell(13).getStringCellValue()).isEqualTo("Submit credentials");

            String sheetText = workbook.getSheetAt(0).getRow(1).getCell(1).getStringCellValue()
                    + workbook.getSheetAt(0).getRow(2).getCell(13).getStringCellValue();
            assertThat(sheetText).doesNotContain("Reset password");
        }
    }

    @Test
    void exportEndpointRejectsSelectedWorkKeysFromAnotherTeam() throws Exception {
        mockMvc.perform(get("/workspace/export")
                        .param("workKeys", "TC-202")
                        .with(user("user@example.com").roles("USER")))
                .andExpect(status().isForbidden());
    }
}