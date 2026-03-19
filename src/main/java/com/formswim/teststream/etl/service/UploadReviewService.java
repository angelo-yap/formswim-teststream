package com.formswim.teststream.etl.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.formswim.teststream.etl.dto.FieldDifference;
import com.formswim.teststream.etl.dto.ReviewApplyResult;
import com.formswim.teststream.etl.dto.ReviewCaseSnapshot;
import com.formswim.teststream.etl.dto.ReviewConflictCandidate;
import com.formswim.teststream.etl.dto.ReviewItemView;
import com.formswim.teststream.etl.dto.ReviewStepSnapshot;
import com.formswim.teststream.etl.dto.UploadReviewSessionView;
import com.formswim.teststream.etl.model.TestCase;
import com.formswim.teststream.etl.model.TestStep;
import com.formswim.teststream.etl.model.UploadHistory;
import com.formswim.teststream.etl.model.UploadReviewItem;
import com.formswim.teststream.etl.model.UploadReviewSession;
import com.formswim.teststream.etl.repository.TestCaseRepository;
import com.formswim.teststream.etl.repository.UploadHistoryRepository;
import com.formswim.teststream.etl.repository.UploadReviewSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class UploadReviewService {

    private final ObjectMapper objectMapper;
    private final UploadDiffService uploadDiffService;
    private final UploadReviewSessionRepository uploadReviewSessionRepository;
    private final UploadHistoryRepository uploadHistoryRepository;
    private final TestCaseRepository testCaseRepository;

    public UploadReviewService(ObjectMapper objectMapper,
                               UploadDiffService uploadDiffService,
                               UploadReviewSessionRepository uploadReviewSessionRepository,
                               UploadHistoryRepository uploadHistoryRepository,
                               TestCaseRepository testCaseRepository) {
        this.objectMapper = objectMapper;
        this.uploadDiffService = uploadDiffService;
        this.uploadReviewSessionRepository = uploadReviewSessionRepository;
        this.uploadHistoryRepository = uploadHistoryRepository;
        this.testCaseRepository = testCaseRepository;
    }

    @Transactional
    public UploadReviewSession createReviewSession(String teamKey,
                                                   String originalFilename,
                                                   String fileHash,
                                                   int parsedTestCaseCount,
                                                   int parsedStepCount,
                                                   List<ReviewCaseSnapshot> newSnapshots,
                                                   int duplicateUnchangedCount,
                                                   List<ReviewConflictCandidate> changedConflicts) {
        UploadReviewSession session = new UploadReviewSession(
            teamKey,
            originalFilename,
            fileHash,
            parsedTestCaseCount,
            parsedStepCount,
            newSnapshots == null ? 0 : newSnapshots.size(),
            changedConflicts == null ? 0 : changedConflicts.size(),
            duplicateUnchangedCount
        );

        if (newSnapshots != null) {
            for (ReviewCaseSnapshot snapshot : newSnapshots) {
                session.addItem(new UploadReviewItem(
                    snapshot.getWorkKey(),
                    UploadReviewItem.TYPE_NEW,
                    null,
                    writeJson(snapshot),
                    "[]"
                ));
            }
        }

        if (changedConflicts != null) {
            for (ReviewConflictCandidate conflict : changedConflicts) {
                session.addItem(new UploadReviewItem(
                    conflict.getWorkKey(),
                    UploadReviewItem.TYPE_CHANGED_DUPLICATE,
                    writeJson(conflict.getExistingSnapshot()),
                    writeJson(conflict.getIncomingSnapshot()),
                    writeJson(conflict.getFieldDifferences())
                ));
            }
        }

        return uploadReviewSessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public Optional<UploadReviewSessionView> getReviewSessionView(String sessionId, String teamKey) {
        return uploadReviewSessionRepository.findByIdAndTeamKey(sessionId, teamKey)
            .filter(session -> UploadReviewSession.STATUS_OPEN.equals(session.getStatus()))
            .map(this::toView);
    }

    @Transactional
    public ReviewApplyResult applyReview(String sessionId, String teamKey, Map<String, String[]> parameters) {
        UploadReviewSession session = findRequiredOpenSession(sessionId, teamKey);

        int importedNewCount = 0;
        int mergedCount = 0;
        int skippedCount = session.getDuplicateUnchangedCount();

        for (UploadReviewItem item : session.getItems()) {
            if (UploadReviewItem.TYPE_NEW.equals(item.getConflictType())) {
                ReviewCaseSnapshot newSnapshot = readSnapshot(item.getIncomingSnapshotJson());
                testCaseRepository.save(toEntity(teamKey, newSnapshot));
                importedNewCount++;
                continue;
            }

            String action = param(parameters, "action_" + item.getId(), UploadReviewItem.ACTION_SKIP);
            item.setChosenAction(action);

            if (UploadReviewItem.ACTION_MERGE.equals(action)) {
                ReviewCaseSnapshot editedSnapshot = buildEditedSnapshot(readSnapshot(item.getIncomingSnapshotJson()), parameters, item.getId());
                item.setEditedSnapshotJson(writeJson(editedSnapshot));
                mergeIntoExisting(teamKey, editedSnapshot);
                mergedCount++;
            } else {
                skippedCount++;
            }
        }

        session.markApplied();
        uploadReviewSessionRepository.save(session);
        uploadHistoryRepository.save(new UploadHistory(session.getTeamKey(), session.getOriginalFilename(), session.getFileHash()));

        return new ReviewApplyResult(importedNewCount, mergedCount, skippedCount);
    }

    @Transactional
    public void cancelReview(String sessionId, String teamKey) {
        UploadReviewSession session = findRequiredOpenSession(sessionId, teamKey);
        session.markCancelled();
        uploadReviewSessionRepository.save(session);
    }

    private UploadReviewSessionView toView(UploadReviewSession session) {
        List<ReviewItemView> conflictItems = new ArrayList<>();
        for (UploadReviewItem item : session.getItems()) {
            if (!UploadReviewItem.TYPE_CHANGED_DUPLICATE.equals(item.getConflictType())) {
                continue;
            }

            ReviewCaseSnapshot existingSnapshot = readSnapshot(item.getExistingSnapshotJson());
            ReviewCaseSnapshot incomingSnapshot = readSnapshot(item.getIncomingSnapshotJson());
            ReviewCaseSnapshot editableSnapshot = item.getEditedSnapshotJson() == null || item.getEditedSnapshotJson().isBlank()
                ? incomingSnapshot.copy()
                : readSnapshot(item.getEditedSnapshotJson());

            conflictItems.add(new ReviewItemView(
                item.getId(),
                item.getWorkKey(),
                item.getChosenAction(),
                readDifferences(item.getChangedFieldsJson()),
                existingSnapshot,
                incomingSnapshot,
                editableSnapshot
            ));
        }

        return new UploadReviewSessionView(
            session.getId(),
            session.getOriginalFilename(),
            session.getParsedTestCaseCount(),
            session.getParsedStepCount(),
            session.getNewItemCount(),
            session.getChangedItemCount(),
            session.getDuplicateUnchangedCount(),
            conflictItems
        );
    }

    private UploadReviewSession findRequiredOpenSession(String sessionId, String teamKey) {
        return uploadReviewSessionRepository.findByIdAndTeamKey(sessionId, teamKey)
            .filter(session -> UploadReviewSession.STATUS_OPEN.equals(session.getStatus()))
            .orElseThrow(() -> new IllegalArgumentException("Review session not found or no longer available."));
    }

    private ReviewCaseSnapshot buildEditedSnapshot(ReviewCaseSnapshot base, Map<String, String[]> parameters, Long itemId) {
        ReviewCaseSnapshot edited = base.copy();
        edited.setSummary(param(parameters, "summary_" + itemId, edited.getSummary()));
        edited.setDescription(param(parameters, "description_" + itemId, edited.getDescription()));
        edited.setPrecondition(param(parameters, "precondition_" + itemId, edited.getPrecondition()));
        edited.setStatus(param(parameters, "status_" + itemId, edited.getStatus()));
        edited.setPriority(param(parameters, "priority_" + itemId, edited.getPriority()));
        edited.setAssignee(param(parameters, "assignee_" + itemId, edited.getAssignee()));
        edited.setReporter(param(parameters, "reporter_" + itemId, edited.getReporter()));
        edited.setEstimatedTime(param(parameters, "estimatedTime_" + itemId, edited.getEstimatedTime()));
        edited.setLabels(param(parameters, "labels_" + itemId, edited.getLabels()));
        edited.setComponents(param(parameters, "components_" + itemId, edited.getComponents()));
        edited.setSprint(param(parameters, "sprint_" + itemId, edited.getSprint()));
        edited.setFixVersions(param(parameters, "fixVersions_" + itemId, edited.getFixVersions()));
        edited.setVersion(param(parameters, "version_" + itemId, edited.getVersion()));
        edited.setFolder(param(parameters, "folder_" + itemId, edited.getFolder()));
        edited.setTestCaseType(param(parameters, "testCaseType_" + itemId, edited.getTestCaseType()));
        edited.setCreatedBy(param(parameters, "createdBy_" + itemId, edited.getCreatedBy()));
        edited.setCreatedOn(param(parameters, "createdOn_" + itemId, edited.getCreatedOn()));
        edited.setUpdatedBy(param(parameters, "updatedBy_" + itemId, edited.getUpdatedBy()));
        edited.setUpdatedOn(param(parameters, "updatedOn_" + itemId, edited.getUpdatedOn()));
        edited.setStoryLinkages(param(parameters, "storyLinkages_" + itemId, edited.getStoryLinkages()));
        edited.setIsSharableStep(param(parameters, "isSharableStep_" + itemId, edited.getIsSharableStep()));
        edited.setFlakyScore(param(parameters, "flakyScore_" + itemId, edited.getFlakyScore()));

        int stepCount = Integer.parseInt(param(parameters, "stepCount_" + itemId, String.valueOf(edited.getSteps().size())));
        List<ReviewStepSnapshot> editedSteps = new ArrayList<>();
        for (int index = 0; index < stepCount; index++) {
            int stepNumber = Integer.parseInt(param(parameters, "stepNumber_" + itemId + "_" + index, String.valueOf(index + 1)));
            editedSteps.add(new ReviewStepSnapshot(
                stepNumber,
                param(parameters, "stepSummary_" + itemId + "_" + index, ""),
                param(parameters, "testData_" + itemId + "_" + index, ""),
                param(parameters, "expectedResult_" + itemId + "_" + index, "")
            ));
        }
        edited.setSteps(editedSteps);
        return edited;
    }

    private void mergeIntoExisting(String teamKey, ReviewCaseSnapshot snapshot) {
        TestCase existing = testCaseRepository.findByTeamKeyAndWorkKey(teamKey, snapshot.getWorkKey())
            .orElseGet(() -> new TestCase(
                teamKey,
                snapshot.getWorkKey(),
                snapshot.getSummary(),
                snapshot.getDescription(),
                snapshot.getPrecondition(),
                snapshot.getStatus(),
                snapshot.getPriority(),
                snapshot.getAssignee(),
                snapshot.getReporter(),
                snapshot.getEstimatedTime(),
                snapshot.getLabels(),
                snapshot.getComponents(),
                snapshot.getSprint(),
                snapshot.getFixVersions(),
                snapshot.getVersion(),
                snapshot.getFolder(),
                snapshot.getTestCaseType(),
                snapshot.getCreatedBy(),
                snapshot.getCreatedOn(),
                snapshot.getUpdatedBy(),
                snapshot.getUpdatedOn(),
                snapshot.getStoryLinkages(),
                snapshot.getIsSharableStep(),
                snapshot.getFlakyScore()
            ));

        applySnapshot(existing, snapshot);
        testCaseRepository.save(existing);
    }

    private TestCase toEntity(String teamKey, ReviewCaseSnapshot snapshot) {
        TestCase testCase = new TestCase(
            teamKey,
            snapshot.getWorkKey(),
            snapshot.getSummary(),
            snapshot.getDescription(),
            snapshot.getPrecondition(),
            snapshot.getStatus(),
            snapshot.getPriority(),
            snapshot.getAssignee(),
            snapshot.getReporter(),
            snapshot.getEstimatedTime(),
            snapshot.getLabels(),
            snapshot.getComponents(),
            snapshot.getSprint(),
            snapshot.getFixVersions(),
            snapshot.getVersion(),
            snapshot.getFolder(),
            snapshot.getTestCaseType(),
            snapshot.getCreatedBy(),
            snapshot.getCreatedOn(),
            snapshot.getUpdatedBy(),
            snapshot.getUpdatedOn(),
            snapshot.getStoryLinkages(),
            snapshot.getIsSharableStep(),
            snapshot.getFlakyScore()
        );
        List<TestStep> steps = new ArrayList<>();
        for (ReviewStepSnapshot step : snapshot.getSteps()) {
            steps.add(new TestStep(step.getStepNumber(), step.getStepSummary(), step.getTestData(), step.getExpectedResult()));
        }
        testCase.replaceSteps(steps);
        return testCase;
    }

    private void applySnapshot(TestCase testCase, ReviewCaseSnapshot snapshot) {
        testCase.setSummary(snapshot.getSummary());
        testCase.setDescription(snapshot.getDescription());
        testCase.setPrecondition(snapshot.getPrecondition());
        testCase.setStatus(snapshot.getStatus());
        testCase.setPriority(snapshot.getPriority());
        testCase.setAssignee(snapshot.getAssignee());
        testCase.setReporter(snapshot.getReporter());
        testCase.setEstimatedTime(snapshot.getEstimatedTime());
        testCase.setLabels(snapshot.getLabels());
        testCase.setComponents(snapshot.getComponents());
        testCase.setSprint(snapshot.getSprint());
        testCase.setFixVersions(snapshot.getFixVersions());
        testCase.setVersion(snapshot.getVersion());
        testCase.setFolder(snapshot.getFolder());
        testCase.setTestCaseType(snapshot.getTestCaseType());
        testCase.setCreatedBy(snapshot.getCreatedBy());
        testCase.setCreatedOn(snapshot.getCreatedOn());
        testCase.setUpdatedBy(snapshot.getUpdatedBy());
        testCase.setUpdatedOn(snapshot.getUpdatedOn());
        testCase.setStoryLinkages(snapshot.getStoryLinkages());
        testCase.setIsSharableStep(snapshot.getIsSharableStep());
        testCase.setFlakyScore(snapshot.getFlakyScore());

        List<TestStep> steps = new ArrayList<>();
        for (ReviewStepSnapshot step : snapshot.getSteps()) {
            steps.add(new TestStep(step.getStepNumber(), step.getStepSummary(), step.getTestData(), step.getExpectedResult()));
        }
        testCase.replaceSteps(steps);
    }

    private ReviewCaseSnapshot readSnapshot(String json) {
        try {
            return objectMapper.readValue(json, ReviewCaseSnapshot.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to read staged review snapshot", exception);
        }
    }

    private List<FieldDifference> readDifferences(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<FieldDifference>>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to read staged field differences", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to store staged review data", exception);
        }
    }

    private String param(Map<String, String[]> parameters, String name, String fallback) {
        String[] values = parameters.get(name);
        if (values == null || values.length == 0) {
            return fallback == null ? "" : fallback;
        }
        return values[0] == null ? "" : values[0];
    }
}
