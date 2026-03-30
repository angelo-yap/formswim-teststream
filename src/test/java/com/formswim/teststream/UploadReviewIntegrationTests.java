package com.formswim.teststream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.formswim.teststream.auth.model.AppUser;
import com.formswim.teststream.auth.repository.UserRepository;
import com.formswim.teststream.ingestion.dto.EtlResultSummary;
import com.formswim.teststream.ingestion.model.UploadReviewItem;
import com.formswim.teststream.ingestion.model.UploadReviewSession;
import com.formswim.teststream.ingestion.repository.UploadHistoryRepository;
import com.formswim.teststream.ingestion.repository.UploadReviewSessionRepository;
import com.formswim.teststream.ingestion.services.ExcelParserService;
import com.formswim.teststream.shared.domain.TestCase;
import com.formswim.teststream.shared.domain.TestCaseRepository;
import com.formswim.teststream.shared.domain.TestStep;
import com.formswim.teststream.support.TestCaseFixtures;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UploadReviewIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestCaseRepository testCaseRepository;

    @Autowired
    private UploadHistoryRepository uploadHistoryRepository;

    @Autowired
    private UploadReviewSessionRepository uploadReviewSessionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        uploadReviewSessionRepository.deleteAll();
        uploadHistoryRepository.deleteAll();
        testCaseRepository.deleteAll();
        userRepository.deleteAll();

        userRepository.save(new AppUser(
            "user@example.com",
            passwordEncoder.encode("Password123"),
            "TEAM1"
        ));
    }

    @Test
    void exactDuplicateFileIsBlockedByHash() throws Exception {
        MockMultipartFile upload = new MockMultipartFile(
            "file",
            "duplicate-check.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            workbookBytes(
                row("TC-100", "First upload", "Draft", "UI", "Open page", "", "Page visible")
            )
        );

        MvcResult firstUpload = mockMvc.perform(multipart("/api/upload")
                .file(upload)
                .with(csrf())
                .with(user("user@example.com").roles("USER")))
            .andExpect(status().isOk())
            .andReturn();

        EtlResultSummary firstResult = objectMapper.readValue(
            firstUpload.getResponse().getContentAsByteArray(),
            EtlResultSummary.class
        );

        assertThat(firstResult.isExactDuplicateFile()).isFalse();
        assertThat(firstResult.isReviewRequired()).isFalse();
        assertThat(firstResult.getImportedCount()).isEqualTo(1);
        assertThat(uploadHistoryRepository.count()).isEqualTo(1);
        assertThat(testCaseRepository.countByTeamKey("TEAM1")).isEqualTo(1);

        MvcResult secondUpload = mockMvc.perform(multipart("/api/upload")
                .file(upload)
                .with(csrf())
                .with(user("user@example.com").roles("USER")))
            .andExpect(status().isOk())
            .andReturn();

        EtlResultSummary secondResult = objectMapper.readValue(
            secondUpload.getResponse().getContentAsByteArray(),
            EtlResultSummary.class
        );

        assertThat(secondResult.isExactDuplicateFile()).isTrue();
        assertThat(secondResult.getMessage()).contains("exact file was already uploaded");
        assertThat(testCaseRepository.countByTeamKey("TEAM1")).isEqualTo(1);
        assertThat(uploadHistoryRepository.count()).isEqualTo(1);
    }

    @Test
    void changedDuplicateCreatesReviewSessionAndApplyMergesIt() throws Exception {
        TestCase existing = TestCaseFixtures.detailedCase(
            "TEAM1",
            "TC-101",
            "Original summary",
            "Original description",
            "Original precondition",
            "Auth/Login"
        );
        existing.addStep(new TestStep(1, "Open login page", "", "Login form shows"));
        testCaseRepository.save(existing);

        MockMultipartFile upload = new MockMultipartFile(
            "file",
            "review-needed.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            workbookBytes(
                rowWithDetails(
                    "TC-101",
                    "Updated summary",
                    "Updated summary description",
                    "Updated summary precondition",
                    "Draft",
                    "UI",
                    "Open login page",
                    "",
                    "Login form shows"
                ),
                row("TC-102", "Brand new case", "Pass", "API", "Call endpoint", "payload", "Success response")
            )
        );

        MvcResult uploadResult = mockMvc.perform(multipart("/api/upload")
                .file(upload)
                .with(csrf())
                .with(user("user@example.com").roles("USER")))
            .andExpect(status().isOk())
            .andReturn();

        EtlResultSummary result = objectMapper.readValue(
            uploadResult.getResponse().getContentAsByteArray(),
            EtlResultSummary.class
        );

        assertThat(result.isReviewRequired()).isTrue();
        assertThat(result.getReviewSessionId()).isNotBlank();
        assertThat(result.getDuplicateChangedCount()).isEqualTo(1);
        assertThat(result.getStagedNewCount()).isEqualTo(1);
        assertThat(testCaseRepository.countByTeamKey("TEAM1")).isEqualTo(1);

        mockMvc.perform(get("/workspace/import/review/{sessionId}", result.getReviewSessionId())
                .with(user("user@example.com").roles("USER")))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Resolve Duplicate Test Cases Before Saving")));

        UploadReviewSession session = uploadReviewSessionRepository
            .findById(result.getReviewSessionId())
            .orElseThrow();

        UploadReviewItem changedItem = session.getItems().stream()
            .filter(item -> UploadReviewItem.TYPE_CHANGED_DUPLICATE.equals(item.getConflictType()))
            .findFirst()
            .orElseThrow();

        mockMvc.perform(post("/workspace/import/review/{sessionId}/apply", result.getReviewSessionId())
                .with(csrf())
                .with(user("user@example.com").roles("USER"))
                .param("action_" + changedItem.getId(), "MERGE")
                .param("stepCount_" + changedItem.getId(), "1")
                .param("stepNumber_" + changedItem.getId() + "_0", "1")
                .param("stepSummary_" + changedItem.getId() + "_0", "Open login page")
                .param("testData_" + changedItem.getId() + "_0", "")
                .param("expectedResult_" + changedItem.getId() + "_0", "Login form shows"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrlPattern("/workspace?importSuccess=*"));

        assertThat(testCaseRepository.countByTeamKey("TEAM1")).isEqualTo(2);
        assertThat(testCaseRepository.findByTeamKeyAndWorkKey("TEAM1", "TC-101"))
            .get()
            .extracting(TestCase::getSummary)
            .isEqualTo("Updated summary");
        assertThat(testCaseRepository.findByTeamKeyAndWorkKey("TEAM1", "TC-102")).isPresent();
        assertThat(uploadHistoryRepository.count()).isEqualTo(1);
        assertThat(uploadReviewSessionRepository.findById(result.getReviewSessionId()))
            .get()
            .extracting(UploadReviewSession::getStatus)
            .isEqualTo(UploadReviewSession.STATUS_APPLIED);
    }

    @Test
    void mergedDuplicateUploadAgainIsTreatedAsUnchanged() throws Exception {
        MockMultipartFile firstUpload = new MockMultipartFile(
            "file",
            "original.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            workbookBytes(
                rowWithDetails(
                    "TC-200",
                    "Original summary",
                    "Original description",
                    "Original precondition",
                    "Draft",
                    "UI",
                    "Open login page",
                    "",
                    "Login form shows"
                )
            )
        );

        MockMultipartFile changedUpload = new MockMultipartFile(
            "file",
            "changed.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            workbookBytes(
                rowWithDetails(
                    "TC-200",
                    "Updated summary",
                    "Line 1\nLine 2",
                    "Updated precondition",
                    "Draft",
                    "UI",
                    "Open login page",
                    "",
                    "Login form shows"
                )
            )
        );

        MockMultipartFile sameLogicalUploadAgain = new MockMultipartFile(
            "file",
            "changed-again.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            workbookBytes(
                List.of(
                    rowWithDetails(
                        "TC-200",
                        "Updated summary",
                        "Line 1\nLine 2",
                        "Updated precondition",
                        "Draft",
                        "UI",
                        "Open login page",
                        "",
                        "Login form shows"
                    ),
                    blankRow()
                )
            )
        );

        MvcResult firstResultRaw = mockMvc.perform(multipart("/api/upload")
                .file(firstUpload)
                .with(csrf())
                .with(user("user@example.com").roles("USER")))
            .andExpect(status().isOk())
            .andReturn();

        EtlResultSummary firstResult = objectMapper.readValue(
            firstResultRaw.getResponse().getContentAsByteArray(),
            EtlResultSummary.class
        );

        assertThat(firstResult.isReviewRequired()).isFalse();
        assertThat(firstResult.getImportedCount()).isEqualTo(1);

        MvcResult secondResultRaw = mockMvc.perform(multipart("/api/upload")
                .file(changedUpload)
                .with(csrf())
                .with(user("user@example.com").roles("USER")))
            .andExpect(status().isOk())
            .andReturn();

        EtlResultSummary secondResult = objectMapper.readValue(
            secondResultRaw.getResponse().getContentAsByteArray(),
            EtlResultSummary.class
        );

        assertThat(secondResult.isReviewRequired()).isTrue();
        assertThat(secondResult.getReviewSessionId()).isNotBlank();

        UploadReviewSession session = uploadReviewSessionRepository
            .findById(secondResult.getReviewSessionId())
            .orElseThrow();

        UploadReviewItem changedItem = session.getItems().stream()
            .filter(item -> UploadReviewItem.TYPE_CHANGED_DUPLICATE.equals(item.getConflictType()))
            .findFirst()
            .orElseThrow();

        mockMvc.perform(post("/workspace/import/review/{sessionId}/apply", secondResult.getReviewSessionId())
                .with(csrf())
                .with(user("user@example.com").roles("USER"))
                .param("action_" + changedItem.getId(), "MERGE")
                .param("description_" + changedItem.getId(), "Line 1\r\nLine 2")
                .param("stepCount_" + changedItem.getId(), "1")
                .param("stepNumber_" + changedItem.getId() + "_0", "1")
                .param("stepSummary_" + changedItem.getId() + "_0", "Open login page")
                .param("testData_" + changedItem.getId() + "_0", "")
                .param("expectedResult_" + changedItem.getId() + "_0", "Login form shows"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrlPattern("/workspace?importSuccess=*"));

        MvcResult thirdResultRaw = mockMvc.perform(multipart("/api/upload")
                .file(sameLogicalUploadAgain)
                .with(csrf())
                .with(user("user@example.com").roles("USER")))
            .andExpect(status().isOk())
            .andReturn();

        EtlResultSummary thirdResult = objectMapper.readValue(
            thirdResultRaw.getResponse().getContentAsByteArray(),
            EtlResultSummary.class
        );

        assertThat(thirdResult.isExactDuplicateFile()).isFalse();
        assertThat(thirdResult.isReviewRequired()).isFalse();
        assertThat(thirdResult.getDuplicateChangedCount()).isEqualTo(0);
        assertThat(thirdResult.getDuplicateUnchangedCount()).isEqualTo(1);
        assertThat(testCaseRepository.countByTeamKey("TEAM1")).isEqualTo(1);
    }

    private byte[] workbookBytes(String[]... rows) throws Exception {
        return workbookBytes(List.of(rows));
    }

    private byte[] workbookBytes(List<String[]> rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            var sheet = workbook.createSheet("Test Cases");
            Row header = sheet.createRow(0);
            List<String> headers = ExcelParserService.CANONICAL_HEADERS;

            for (int index = 0; index < headers.size(); index++) {
                header.createCell(index).setCellValue(headers.get(index));
            }

            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                Row row = sheet.createRow(rowIndex + 1);
                String[] values = rows.get(rowIndex);

                for (int columnIndex = 0; columnIndex < values.length; columnIndex++) {
                    if (values[columnIndex] != null) {
                        row.createCell(columnIndex).setCellValue(values[columnIndex]);
                    }
                }
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private String[] row(String workKey,
                         String summary,
                         String status,
                         String components,
                         String stepSummary,
                         String testData,
                         String expectedResult) {
        String[] values = new String[ExcelParserService.CANONICAL_HEADERS.size()];
        values[0] = workKey;
        values[1] = summary;
        values[2] = summary + " description";
        values[3] = summary + " precondition";
        values[4] = status;
        values[5] = "High";
        values[6] = "Alice";
        values[7] = "Bob";
        values[8] = "5m";
        values[9] = "auth";
        values[10] = components;
        values[11] = "Sprint 1";
        values[12] = "1.0";
        values[13] = stepSummary;
        values[14] = testData;
        values[15] = expectedResult;
        values[16] = "V1";
        values[17] = "Folder/A";
        values[18] = "Regression";
        values[19] = "creator@example.com";
        values[20] = "2026-03-01";
        values[21] = "editor@example.com";
        values[22] = "2026-03-02";
        values[23] = "ST-1";
        values[24] = "No";
        values[25] = "1";
        return values;
    }

    private String[] rowWithDetails(String workKey,
                                    String summary,
                                    String description,
                                    String precondition,
                                    String status,
                                    String components,
                                    String stepSummary,
                                    String testData,
                                    String expectedResult) {
        String[] values = row(workKey, summary, status, components, stepSummary, testData, expectedResult);
        values[2] = description;
        values[3] = precondition;
        return values;
    }

    private String[] blankRow() {
        return new String[ExcelParserService.CANONICAL_HEADERS.size()];
    }
}