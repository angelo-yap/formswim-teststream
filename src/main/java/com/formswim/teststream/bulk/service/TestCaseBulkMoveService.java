package com.formswim.teststream.bulk.service;

import com.formswim.teststream.bulk.dto.BulkMoveRequest;
import com.formswim.teststream.bulk.dto.BulkMoveResult;
import com.formswim.teststream.shared.domain.TestCaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

        List<String> normalizedWorkKeys = new ArrayList<>(normalizedUniqueWorkKeys);
        Set<String> ownedWorkKeys = new HashSet<>(testCaseRepository.findOwnedWorkKeysIn(teamKey, normalizedWorkKeys));
        Set<String> existingWorkKeys = new HashSet<>(testCaseRepository.findExistingWorkKeysIn(normalizedWorkKeys));

        List<String> allowedWorkKeys = new ArrayList<>();
        for (String workKey : normalizedWorkKeys) {
            if (ownedWorkKeys.contains(workKey)) {
                allowedWorkKeys.add(workKey);
                continue;
            }

            // Classify each non-owned key for frontend feedback without per-key database calls.
            if (existingWorkKeys.contains(workKey)) {
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
        // Apply one scoped update statement for all eligible keys in this request.
        int movedCount = testCaseRepository.bulkMoveToFolderByTeamKeyAndWorkKeys(teamKey, allowedWorkKeys, trimmedTargetFolder);
        result.setMovedCount(movedCount);
        return result;
    }

    private void addFailure(BulkMoveResult result, String workKey, String reason) {
        result.getFailures().add(new BulkMoveResult.BulkMoveFailure(workKey, reason));
    }
}
