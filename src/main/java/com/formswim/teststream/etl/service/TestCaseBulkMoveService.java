package com.formswim.teststream.etl.service;

import com.formswim.teststream.etl.dto.BulkMoveRequest;
import com.formswim.teststream.etl.dto.BulkMoveResult;
import com.formswim.teststream.etl.model.TestCase;
import com.formswim.teststream.etl.repository.TestCaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class TestCaseBulkMoveService {

    private static final String FAILURE_INVALID = "INVALID_WORK_KEY";
    private static final String FAILURE_FORBIDDEN = "FORBIDDEN";
    private static final String FAILURE_NOT_FOUND = "NOT_FOUND";

    private final TestCaseRepository testCaseRepository;

    public TestCaseBulkMoveService(TestCaseRepository testCaseRepository) {
        this.testCaseRepository = testCaseRepository;
    }

    @Transactional
    public BulkMoveResult bulkMoveByWorkKeys(String teamKey, BulkMoveRequest request) {
        BulkMoveResult result = new BulkMoveResult();
        List<String> requestedWorkKeys = request.getWorkKeys();
        result.setRequestedCount(requestedWorkKeys.size());

        // Normalize incoming keys while preserving first-seen order for predictable response payloads.
        LinkedHashSet<String> normalizedUniqueWorkKeys = new LinkedHashSet<>();
        for (String rawWorkKey : requestedWorkKeys) {
            if (rawWorkKey == null) {
                addFailure(result, null, FAILURE_INVALID);
                result.setInvalidCount(result.getInvalidCount() + 1);
                continue;
            }

            String normalized = rawWorkKey.trim();
            if (normalized.isBlank()) {
                addFailure(result, rawWorkKey, FAILURE_INVALID);
                result.setInvalidCount(result.getInvalidCount() + 1);
                continue;
            }
            normalizedUniqueWorkKeys.add(normalized);
        }

        if (normalizedUniqueWorkKeys.isEmpty()) {
            return result;
        }

        List<String> allowedWorkKeys = new ArrayList<>();
        for (String workKey : normalizedUniqueWorkKeys) {
            if (testCaseRepository.existsByTeamKeyAndWorkKey(teamKey, workKey)) {
                allowedWorkKeys.add(workKey);
                continue;
            }

            // Distinguish between cross-team keys and truly missing keys for frontend feedback.
            boolean existsInAnotherTeam = testCaseRepository.countByWorkKeyIn(List.of(workKey)) > 0;
            if (existsInAnotherTeam) {
                addFailure(result, workKey, FAILURE_FORBIDDEN);
                result.setForbiddenCount(result.getForbiddenCount() + 1);
            } else {
                addFailure(result, workKey, FAILURE_NOT_FOUND);
                result.setNotFoundCount(result.getNotFoundCount() + 1);
            }
        }

        if (allowedWorkKeys.isEmpty()) {
            return result;
        }

        String trimmedTargetFolder = request.getTargetFolder().trim();
        List<TestCase> testCasesToMove = testCaseRepository.findAllWithStepsByTeamKeyAndWorkKeyIn(teamKey, allowedWorkKeys);
        for (TestCase testCase : testCasesToMove) {
            testCase.setFolder(trimmedTargetFolder);
        }
        testCaseRepository.saveAll(testCasesToMove);
        result.setMovedCount(testCasesToMove.size());
        return result;
    }

    private void addFailure(BulkMoveResult result, String workKey, String reason) {
        result.getFailures().add(new BulkMoveResult.BulkMoveFailure(workKey, reason));
    }
}
