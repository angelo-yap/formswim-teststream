package com.formswim.teststream.testcase.services;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.formswim.teststream.shared.domain.TestCase;
import com.formswim.teststream.shared.domain.TestCaseRepository;
import com.formswim.teststream.testcase.dto.TestCaseBulkDeleteRequest;
import com.formswim.teststream.testcase.dto.TestCaseBulkDeleteResponse;
import com.formswim.teststream.testcase.dto.TestCaseCreateRequest;
import com.formswim.teststream.testcase.dto.TestCaseCreateResponse;
import com.formswim.teststream.workspace.services.FolderPathSyncService;

@Service
public class WorkspaceTestCaseMutationService {

    private static final int MAX_WORK_KEY_LENGTH = 100;
    private static final int MAX_TESTCASE_NAME_LENGTH = 255;
    private static final String FOLDER_SYNC_ACTOR = "testcase-create";

    private final TestCaseRepository testCaseRepository;
    private final FolderPathSyncService folderPathSyncService;

    public WorkspaceTestCaseMutationService(TestCaseRepository testCaseRepository,
                                            FolderPathSyncService folderPathSyncService) {
        this.testCaseRepository = testCaseRepository;
        this.folderPathSyncService = folderPathSyncService;
    }

    @Transactional
    public TestCaseCreateResponse createBlankTestCase(String teamKey, String actorEmail, TestCaseCreateRequest request) {
        if (request == null) {
            throw new TestCaseBadRequestException("Request body is required.");
        }

        String workKey = normalizeWorkKey(request.getWorkKey());
        String name = normalizeName(request.getName());
        String folder = normalizeFolder(request.getFolder());
        String actor = normalizeActor(actorEmail);

        try {
            if (!folder.isBlank()) {
                folderPathSyncService.ensureFolderPathExists(teamKey, folder, FOLDER_SYNC_ACTOR);
            }
        } catch (IllegalArgumentException ex) {
            throw new TestCaseBadRequestException(ex.getMessage());
        }

        if (testCaseRepository.existsByTeamKeyAndWorkKey(teamKey, workKey)) {
            throw new TestCaseConflictException("A testcase with this ID already exists.");
        }

        TestCase testCase = new TestCase(
            teamKey,
            workKey,
            name,
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            folder,
            "",
            actor,
            "",
            actor,
            "",
            "",
            "",
            ""
        );

        TestCase saved = testCaseRepository.save(testCase);
        return new TestCaseCreateResponse(saved.getId(), saved.getWorkKey(), saved.getSummary(), saved.getFolder());
    }

    @Transactional
    public void deleteTestCase(String teamKey, String rawWorkKey) {
        String workKey = normalizeDeleteWorkKey(rawWorkKey);
        TestCase testCase = testCaseRepository.findByTeamKeyAndWorkKey(teamKey, workKey)
            .orElseThrow(() -> new TestCaseNotFoundException("Testcase not found."));
        testCaseRepository.delete(testCase);
    }

    @Transactional
    public TestCaseBulkDeleteResponse bulkDeleteTestCases(String teamKey, TestCaseBulkDeleteRequest request) {
        if (request == null || request.getWorkKeys() == null || request.getWorkKeys().isEmpty()) {
            throw new TestCaseBadRequestException("At least one testcase ID is required.");
        }

        List<String> normalizedWorkKeys = request.getWorkKeys().stream()
            .map(this::normalizeDeleteWorkKey)
            .distinct()
            .toList();

        if (normalizedWorkKeys.isEmpty()) {
            throw new TestCaseBadRequestException("At least one testcase ID is required.");
        }

        List<String> owned = testCaseRepository.findOwnedWorkKeysIn(teamKey, normalizedWorkKeys);
        if (owned.isEmpty()) {
            return new TestCaseBulkDeleteResponse(normalizedWorkKeys.size(), 0, normalizedWorkKeys.size());
        }

        List<TestCase> ownedCases = testCaseRepository.findAllWithStepsByTeamKeyAndWorkKeyIn(teamKey, owned);
        int deletedCount = ownedCases.size();
        if (deletedCount > 0) {
            testCaseRepository.deleteAll(ownedCases);
            testCaseRepository.flush();
        }
        int missingCount = Math.max(normalizedWorkKeys.size() - deletedCount, 0);
        return new TestCaseBulkDeleteResponse(normalizedWorkKeys.size(), deletedCount, missingCount);
    }

    private String normalizeWorkKey(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank()) {
            throw new TestCaseBadRequestException("Testcase ID is required.");
        }
        if (value.length() > MAX_WORK_KEY_LENGTH) {
            throw new TestCaseBadRequestException("Testcase ID cannot exceed 100 characters.");
        }
        return value;
    }

    private String normalizeName(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank()) {
            throw new TestCaseBadRequestException("Testcase name is required.");
        }
        if (value.length() > MAX_TESTCASE_NAME_LENGTH) {
            throw new TestCaseBadRequestException("Testcase name cannot exceed 255 characters.");
        }
        return value;
    }

    private String normalizeFolder(String raw) {
        if (raw == null) {
            return "";
        }
        String normalized = raw.trim().replace('\\', '/').replaceAll("/+", "/");
        return normalized.replaceAll("^/+", "").replaceAll("/+$", "").trim();
    }

    private String normalizeActor(String actorEmail) {
        if (actorEmail == null || actorEmail.isBlank()) {
            return "unknown";
        }
        return actorEmail.trim();
    }

    private String normalizeDeleteWorkKey(String rawWorkKey) {
        String workKey = rawWorkKey == null ? "" : rawWorkKey.trim();
        if (workKey.isBlank()) {
            throw new TestCaseBadRequestException("Testcase ID is required.");
        }
        if (workKey.length() > MAX_WORK_KEY_LENGTH) {
            throw new TestCaseBadRequestException("Testcase ID cannot exceed 100 characters.");
        }
        return workKey;
    }
}
