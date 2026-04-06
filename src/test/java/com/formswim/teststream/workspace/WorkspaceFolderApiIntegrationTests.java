package com.formswim.teststream.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.formswim.teststream.auth.model.AppUser;
import com.formswim.teststream.auth.repository.UserRepository;
import com.formswim.teststream.shared.domain.TestCase;
import com.formswim.teststream.shared.domain.TestCaseRepository;
import com.formswim.teststream.support.TestCaseFixtures;
import com.formswim.teststream.workspace.repository.FolderRepository;
import com.formswim.teststream.workspace.services.FolderBackfillService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkspaceFolderApiIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestCaseRepository testCaseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private FolderBackfillService folderBackfillService;

    @BeforeEach
    void setUp() {
        testCaseRepository.deleteAll();
        folderRepository.deleteAll();
        userRepository.deleteAll();

        userRepository.save(new AppUser("team1.user@example.com", "test-hash", "TEAM1"));
        userRepository.save(new AppUser("team2.user@example.com", "test-hash", "TEAM2"));
    }

    @Test
    void getFoldersReturnsBackfilledPathsFromFolderTable() throws Exception {
        testCaseRepository.saveAll(List.of(
            TestCaseFixtures.basicCase("TEAM1", "TC-101", "Payments/Core"),
            TestCaseFixtures.basicCase("TEAM1", "TC-102", "Payments/Refunds")
        ));

        int created = folderBackfillService.backfillFoldersFromTestCases();
        assertThat(created).isEqualTo(3);

        mockMvc.perform(get("/api/folders")
                .with(user("team1.user@example.com").roles("USER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0]").value("Payments"))
            .andExpect(jsonPath("$[1]").value("Payments/Core"))
            .andExpect(jsonPath("$[2]").value("Payments/Refunds"));
    }

    @Test
    void postFolderCreatesRootAndChild() throws Exception {
        Long rootId = createFolder("Project", null);

        mockMvc.perform(post("/api/folders")
                .with(csrf())
                .with(user("team1.user@example.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of("name", "Sprint-1", "parentId", rootId))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Sprint-1"))
            .andExpect(jsonPath("$.parentId").value(rootId))
            .andExpect(jsonPath("$.path").value("Project/Sprint-1"));
    }

    @Test
    void postTestCaseCreatesBlankCaseWithManualIdAndName() throws Exception {
        mockMvc.perform(post("/api/testcases")
                .with(csrf())
                .with(user("team1.user@example.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of(
                    "workKey", "TC-NEW-001",
                    "name", "New blank testcase",
                    "folder", "Project/Sprint-1"
                ))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.workKey").value("TC-NEW-001"))
            .andExpect(jsonPath("$.name").value("New blank testcase"))
            .andExpect(jsonPath("$.folder").value("Project/Sprint-1"));

        TestCase created = testCaseRepository.findByTeamKeyAndWorkKey("TEAM1", "TC-NEW-001").orElseThrow();
        assertThat(created.getSummary()).isEqualTo("New blank testcase");
        assertThat(created.getDescription()).isEmpty();
        assertThat(created.getFolder()).isEqualTo("Project/Sprint-1");
    }

    @Test
    void postTestCaseWithFolderSyncsFolderNodes() throws Exception {
        mockMvc.perform(post("/api/testcases")
                .with(csrf())
                .with(user("team1.user@example.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of(
                    "workKey", "TC-SYNC-001",
                    "name", "Folder sync testcase",
                    "folder", "Quality/Smoke"
                ))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.workKey").value("TC-SYNC-001"))
            .andExpect(jsonPath("$.folder").value("Quality/Smoke"));

        MvcResult folderNodesResult = mockMvc.perform(get("/api/folders/nodes")
                .with(user("team1.user@example.com").roles("USER")))
            .andExpect(status().isOk())
            .andReturn();

        List<Map<String, Object>> nodes = objectMapper.readValue(
            folderNodesResult.getResponse().getContentAsByteArray(),
            new TypeReference<List<Map<String, Object>>>() {}
        );
        List<String> paths = nodes.stream()
            .map(node -> String.valueOf(node.get("path")))
            .toList();

        assertThat(paths).contains("Quality", "Quality/Smoke");
    }

    @Test
    void postTestCaseRejectsOversizedFolderSegment() throws Exception {
        String oversized = "A".repeat(256);

        mockMvc.perform(post("/api/testcases")
                .with(csrf())
                .with(user("team1.user@example.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of(
                    "workKey", "TC-SYNC-OVERSIZED",
                    "name", "Invalid folder testcase",
                    "folder", oversized + "/Child"
                ))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Folder segment exceeds max length of 255 characters."));

        assertThat(testCaseRepository.findByTeamKeyAndWorkKey("TEAM1", "TC-SYNC-OVERSIZED")).isEmpty();
    }

    @Test
    void postTestCaseRejectsDuplicateManualIdInTeam() throws Exception {
        testCaseRepository.save(TestCaseFixtures.basicCase("TEAM1", "TC-DUP-001", ""));

        mockMvc.perform(post("/api/testcases")
                .with(csrf())
                .with(user("team1.user@example.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of(
                    "workKey", "TC-DUP-001",
                    "name", "Another testcase"
                ))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("A testcase with this ID already exists."));
    }

    @Test
    void postTestCaseRejectsBlankManualId() throws Exception {
        mockMvc.perform(post("/api/testcases")
                .with(csrf())
                .with(user("team1.user@example.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of(
                    "workKey", "   ",
                    "name", "Blank id testcase"
                ))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Testcase ID is required."));
    }

    @Test
    void deleteTestCaseDeletesOwnedCaseByWorkKey() throws Exception {
        testCaseRepository.save(TestCaseFixtures.basicCase("TEAM1", "TC-DEL-001", "Project/Sprint-1"));

        mockMvc.perform(delete("/api/testcases/{workKey}", "TC-DEL-001")
                .with(csrf())
                .with(user("team1.user@example.com").roles("USER")))
            .andExpect(status().isNoContent());

        assertThat(testCaseRepository.findByTeamKeyAndWorkKey("TEAM1", "TC-DEL-001")).isEmpty();
    }

    @Test
    void deleteTestCaseReturnsNotFoundForMissingOrForeignCase() throws Exception {
        testCaseRepository.save(TestCaseFixtures.basicCase("TEAM2", "TC-DEL-002", ""));

        mockMvc.perform(delete("/api/testcases/{workKey}", "TC-DEL-002")
                .with(csrf())
                .with(user("team1.user@example.com").roles("USER")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Testcase not found."));
    }

            @Test
            void deleteTestCaseRejectsOversizedWorkKey() throws Exception {
            String oversized = "A".repeat(101);

            mockMvc.perform(delete("/api/testcases/{workKey}", oversized)
                .with(csrf())
                .with(user("team1.user@example.com").roles("USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Testcase ID cannot exceed 100 characters."));
            }

    @Test
    void bulkDeleteTestCasesDeletesOwnedKeysInSingleRequest() throws Exception {
        testCaseRepository.saveAll(List.of(
            TestCaseFixtures.basicCase("TEAM1", "TC-BDEL-001", ""),
            TestCaseFixtures.basicCase("TEAM1", "TC-BDEL-002", "")
        ));

        mockMvc.perform(post("/api/testcases/bulk-delete")
                .with(csrf())
                .with(user("team1.user@example.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of("workKeys", List.of("TC-BDEL-001", "TC-BDEL-002")))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.requestedCount").value(2))
            .andExpect(jsonPath("$.deletedCount").value(2))
            .andExpect(jsonPath("$.missingCount").value(0));

        assertThat(testCaseRepository.findByTeamKeyAndWorkKey("TEAM1", "TC-BDEL-001")).isEmpty();
        assertThat(testCaseRepository.findByTeamKeyAndWorkKey("TEAM1", "TC-BDEL-002")).isEmpty();
    }

    @Test
    void bulkDeleteTestCasesRejectsEmptyPayload() throws Exception {
        mockMvc.perform(post("/api/testcases/bulk-delete")
                .with(csrf())
                .with(user("team1.user@example.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of("workKeys", List.of()))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("At least one testcase ID is required."));
    }

            @Test
            void postFolderRejectsNameLongerThan255Characters() throws Exception {
            String oversized = "A".repeat(256);

            mockMvc.perform(post("/api/folders")
                .with(csrf())
                .with(user("team1.user@example.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of("name", oversized))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Folder name cannot exceed 255 characters."));
            }

            @Test
            void postFolderReturnsNotFoundWhenParentMissing() throws Exception {
            mockMvc.perform(post("/api/folders")
                .with(csrf())
                .with(user("team1.user@example.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of("name", "Sprint-1", "parentId", 99999L))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Parent folder not found."));
            }

            @Test
            void patchFolderReturnsNotFoundWhenFolderMissing() throws Exception {
            mockMvc.perform(patch("/api/folders/{id}", 99999L)
                .with(csrf())
                .with(user("team1.user@example.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of("name", "Renamed"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Folder not found."));
            }

            @Test
            void postFolderRejectsDuplicateRootNameIgnoringCase() throws Exception {
            createFolder("Project", null);

            mockMvc.perform(post("/api/folders")
                .with(csrf())
                .with(user("team1.user@example.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of("name", "project"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A folder with this name already exists in the target location."));
            }

    @Test
    void patchFolderRejectsCycles() throws Exception {
        Long rootId = createFolder("Root", null);
        Long childId = createFolder("Child", rootId);

        mockMvc.perform(patch("/api/folders/{id}", rootId)
                .with(csrf())
                .with(user("team1.user@example.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of("parentId", childId))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Cannot move a folder into one of its descendants."));
    }

    @Test
    void deleteFolderReturnsConflictWhenFolderHasChildren() throws Exception {
        Long rootId = createFolder("Root", null);
        createFolder("Child", rootId);

        mockMvc.perform(delete("/api/folders/{id}", rootId)
                .with(csrf())
                .with(user("team1.user@example.com").roles("USER")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Folder cannot be deleted because it contains subfolders."));
    }

    @Test
    void deleteFolderReturnsConflictWhenFolderContainsTestCases() throws Exception {
        Long rootId = createFolder("Root", null);
        createFolder("Child", rootId);

        TestCase testCase = TestCaseFixtures.basicCase("TEAM1", "TC-101", "Root/Child");
        testCaseRepository.save(testCase);

        MvcResult childNodeResult = mockMvc.perform(get("/api/folders/nodes")
                .with(user("team1.user@example.com").roles("USER")))
            .andExpect(status().isOk())
            .andReturn();

        List<Map<String, Object>> nodes = objectMapper.readValue(
            childNodeResult.getResponse().getContentAsByteArray(),
            new TypeReference<List<Map<String, Object>>>() {}
        );
        Long childId = nodes.stream()
            .filter(node -> "Root/Child".equals(String.valueOf(node.get("path"))))
            .map(node -> Long.valueOf(String.valueOf(node.get("id"))))
            .findFirst()
            .orElseThrow();

        mockMvc.perform(delete("/api/folders/{id}", childId)
                .with(csrf())
                .with(user("team1.user@example.com").roles("USER")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Folder cannot be deleted because it contains test cases."));
    }

    @Test
    void deleteFolderReturnsConflictWhenFolderContainsTestCasesWithLeadingSlashPath() throws Exception {
        Long rootId = createFolder("Root", null);
        createFolder("Child", rootId);

        TestCase testCase = TestCaseFixtures.basicCase("TEAM1", "TC-102", "/Root/Child");
        testCaseRepository.save(testCase);

        MvcResult childNodeResult = mockMvc.perform(get("/api/folders/nodes")
                .with(user("team1.user@example.com").roles("USER")))
            .andExpect(status().isOk())
            .andReturn();

        List<Map<String, Object>> nodes = objectMapper.readValue(
            childNodeResult.getResponse().getContentAsByteArray(),
            new TypeReference<List<Map<String, Object>>>() {}
        );
        Long childId = nodes.stream()
            .filter(node -> "Root/Child".equals(String.valueOf(node.get("path"))))
            .map(node -> Long.valueOf(String.valueOf(node.get("id"))))
            .findFirst()
            .orElseThrow();

        mockMvc.perform(delete("/api/folders/{id}", childId)
                .with(csrf())
                .with(user("team1.user@example.com").roles("USER")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Folder cannot be deleted because it contains test cases."));
    }

    @Test
    void deleteFolderRemovesEmptyLeaf() throws Exception {
        Long rootId = createFolder("Root", null);
        Long childId = createFolder("Child", rootId);

        mockMvc.perform(delete("/api/folders/{id}", childId)
                .with(csrf())
                .with(user("team1.user@example.com").roles("USER")))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/folders")
                .with(user("team1.user@example.com").roles("USER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0]").value("Root"))
            .andExpect(jsonPath("$[1]").doesNotExist());
    }

    @Test
    void patchFolderReparentUpdatesTestCaseFolderPathsInSubtree() throws Exception {
        Long sourceRootId = createFolder("Source", null);
        createFolder("Legacy", sourceRootId);
        Long targetRootId = createFolder("Target", null);

        testCaseRepository.saveAll(List.of(
            TestCaseFixtures.basicCase("TEAM1", "TC-111", "Source/Legacy"),
            TestCaseFixtures.basicCase("TEAM1", "TC-112", "Source/Legacy/Child"),
            TestCaseFixtures.basicCase("TEAM1", "TC-113", "Elsewhere")
        ));

        MvcResult childNodeResult = mockMvc.perform(get("/api/folders/nodes")
                .with(user("team1.user@example.com").roles("USER")))
            .andExpect(status().isOk())
            .andReturn();

        List<Map<String, Object>> nodes = objectMapper.readValue(
            childNodeResult.getResponse().getContentAsByteArray(),
            new TypeReference<List<Map<String, Object>>>() {}
        );

        Long legacyId = nodes.stream()
            .filter(node -> "Source/Legacy".equals(String.valueOf(node.get("path"))))
            .map(node -> Long.valueOf(String.valueOf(node.get("id"))))
            .findFirst()
            .orElseThrow();

        mockMvc.perform(patch("/api/folders/{id}", legacyId)
                .with(csrf())
                .with(user("team1.user@example.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of("parentId", targetRootId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.path").value("Target/Legacy"));

        assertThat(testCaseRepository.findByTeamKeyAndWorkKey("TEAM1", "TC-111").orElseThrow().getFolder())
            .isEqualTo("Target/Legacy");
        assertThat(testCaseRepository.findByTeamKeyAndWorkKey("TEAM1", "TC-112").orElseThrow().getFolder())
            .isEqualTo("Target/Legacy/Child");
        assertThat(testCaseRepository.findByTeamKeyAndWorkKey("TEAM1", "TC-113").orElseThrow().getFolder())
            .isEqualTo("Elsewhere");
    }

    @Test
    void patchFolderReparentUpdatesTestCaseFolderPathsWithLeadingSlashInSubtree() throws Exception {
        Long sourceRootId = createFolder("Source", null);
        createFolder("Legacy", sourceRootId);
        Long targetRootId = createFolder("Target", null);

        testCaseRepository.saveAll(List.of(
            TestCaseFixtures.basicCase("TEAM1", "TC-211", "/Source/Legacy"),
            TestCaseFixtures.basicCase("TEAM1", "TC-212", "/Source/Legacy/Child")
        ));

        MvcResult childNodeResult = mockMvc.perform(get("/api/folders/nodes")
                .with(user("team1.user@example.com").roles("USER")))
            .andExpect(status().isOk())
            .andReturn();

        List<Map<String, Object>> nodes = objectMapper.readValue(
            childNodeResult.getResponse().getContentAsByteArray(),
            new TypeReference<List<Map<String, Object>>>() {}
        );

        Long legacyId = nodes.stream()
            .filter(node -> "Source/Legacy".equals(String.valueOf(node.get("path"))))
            .map(node -> Long.valueOf(String.valueOf(node.get("id"))))
            .findFirst()
            .orElseThrow();

        mockMvc.perform(patch("/api/folders/{id}", legacyId)
                .with(csrf())
                .with(user("team1.user@example.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of("parentId", targetRootId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.path").value("Target/Legacy"));

        assertThat(testCaseRepository.findByTeamKeyAndWorkKey("TEAM1", "TC-211").orElseThrow().getFolder())
            .isEqualTo("/Target/Legacy");
        assertThat(testCaseRepository.findByTeamKeyAndWorkKey("TEAM1", "TC-212").orElseThrow().getFolder())
            .isEqualTo("/Target/Legacy/Child");
    }

    @Test
    void backfillSkipsOversizedSegmentsWithoutThrowing() {
        String oversized = "A".repeat(256);
        testCaseRepository.save(TestCaseFixtures.basicCase("TEAM1", "TC-901", oversized + "/Child"));

        int created = folderBackfillService.backfillFoldersFromTestCases();
        assertThat(created).isEqualTo(0);
        assertThat(folderRepository.findByTeamKeyOrderByIdAsc("TEAM1")).isEmpty();
    }

    private Long createFolder(String name, Long parentId) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", name);
        if (parentId != null) {
            payload.put("parentId", parentId);
        }

        MvcResult result = mockMvc.perform(post("/api/folders")
                .with(csrf())
                .with(user("team1.user@example.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(payload)))
            .andExpect(status().isCreated())
            .andReturn();

        Map<String, Object> body = objectMapper.readValue(
            result.getResponse().getContentAsByteArray(),
            new TypeReference<Map<String, Object>>() {}
        );
        return Long.valueOf(String.valueOf(body.get("id")));
    }
}