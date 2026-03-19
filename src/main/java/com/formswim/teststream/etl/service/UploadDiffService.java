package com.formswim.teststream.etl.service;

import com.formswim.teststream.etl.dto.FieldDifference;
import com.formswim.teststream.etl.dto.ReviewCaseSnapshot;
import com.formswim.teststream.etl.dto.ReviewConflictCandidate;
import com.formswim.teststream.etl.dto.ReviewStepSnapshot;
import com.formswim.teststream.etl.model.TestCase;
import com.formswim.teststream.etl.model.TestStep;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class UploadDiffService {

    public ReviewCaseSnapshot toSnapshot(TestCase testCase) {
        ReviewCaseSnapshot snapshot = new ReviewCaseSnapshot();
        snapshot.setWorkKey(testCase.getWorkKey());
        snapshot.setSummary(testCase.getSummary());
        snapshot.setDescription(testCase.getDescription());
        snapshot.setPrecondition(testCase.getPrecondition());
        snapshot.setStatus(testCase.getStatus());
        snapshot.setPriority(testCase.getPriority());
        snapshot.setAssignee(testCase.getAssignee());
        snapshot.setReporter(testCase.getReporter());
        snapshot.setEstimatedTime(testCase.getEstimatedTime());
        snapshot.setLabels(testCase.getLabels());
        snapshot.setComponents(testCase.getComponents());
        snapshot.setSprint(testCase.getSprint());
        snapshot.setFixVersions(testCase.getFixVersions());
        snapshot.setVersion(testCase.getVersion());
        snapshot.setFolder(testCase.getFolder());
        snapshot.setTestCaseType(testCase.getTestCaseType());
        snapshot.setCreatedBy(testCase.getCreatedBy());
        snapshot.setCreatedOn(testCase.getCreatedOn());
        snapshot.setUpdatedBy(testCase.getUpdatedBy());
        snapshot.setUpdatedOn(testCase.getUpdatedOn());
        snapshot.setStoryLinkages(testCase.getStoryLinkages());
        snapshot.setIsSharableStep(testCase.getIsSharableStep());
        snapshot.setFlakyScore(testCase.getFlakyScore());

        List<ReviewStepSnapshot> steps = new ArrayList<>();
        for (TestStep step : testCase.getSteps()) {
            steps.add(new ReviewStepSnapshot(step.getStepNumber(), step.getStepSummary(), step.getTestData(), step.getExpectedResult()));
        }
        snapshot.setSteps(steps);
        return snapshot;
    }

    public ReviewConflictCandidate buildConflict(TestCase existing, TestCase incoming) {
        ReviewCaseSnapshot existingSnapshot = toSnapshot(existing);
        ReviewCaseSnapshot incomingSnapshot = toSnapshot(incoming);
        return new ReviewConflictCandidate(incoming.getWorkKey(), existingSnapshot, incomingSnapshot, diff(existingSnapshot, incomingSnapshot));
    }

    public boolean isEquivalent(TestCase existing, TestCase incoming) {
        return diff(toSnapshot(existing), toSnapshot(incoming)).isEmpty();
    }

    public List<FieldDifference> diff(ReviewCaseSnapshot existing, ReviewCaseSnapshot incoming) {
        List<FieldDifference> differences = new ArrayList<>();
        addDifference(differences, "summary", "Summary", existing.getSummary(), incoming.getSummary());
        addDifference(differences, "description", "Description", existing.getDescription(), incoming.getDescription());
        addDifference(differences, "precondition", "Precondition", existing.getPrecondition(), incoming.getPrecondition());
        addDifference(differences, "status", "Status", existing.getStatus(), incoming.getStatus());
        addDifference(differences, "priority", "Priority", existing.getPriority(), incoming.getPriority());
        addDifference(differences, "assignee", "Assignee", existing.getAssignee(), incoming.getAssignee());
        addDifference(differences, "reporter", "Reporter", existing.getReporter(), incoming.getReporter());
        addDifference(differences, "estimatedTime", "Estimated time", existing.getEstimatedTime(), incoming.getEstimatedTime());
        addDifference(differences, "labels", "Labels", existing.getLabels(), incoming.getLabels());
        addDifference(differences, "components", "Components", existing.getComponents(), incoming.getComponents());
        addDifference(differences, "sprint", "Sprint", existing.getSprint(), incoming.getSprint());
        addDifference(differences, "fixVersions", "Fix versions", existing.getFixVersions(), incoming.getFixVersions());
        addDifference(differences, "version", "Version", existing.getVersion(), incoming.getVersion());
        addDifference(differences, "folder", "Folder", existing.getFolder(), incoming.getFolder());
        addDifference(differences, "testCaseType", "Test case type", existing.getTestCaseType(), incoming.getTestCaseType());
        addDifference(differences, "createdBy", "Created by", existing.getCreatedBy(), incoming.getCreatedBy());
        addDifference(differences, "createdOn", "Created on", existing.getCreatedOn(), incoming.getCreatedOn());
        addDifference(differences, "updatedBy", "Updated by", existing.getUpdatedBy(), incoming.getUpdatedBy());
        addDifference(differences, "updatedOn", "Updated on", existing.getUpdatedOn(), incoming.getUpdatedOn());
        addDifference(differences, "storyLinkages", "Story linkages", existing.getStoryLinkages(), incoming.getStoryLinkages());
        addDifference(differences, "isSharableStep", "Is shareable step", existing.getIsSharableStep(), incoming.getIsSharableStep());
        addDifference(differences, "flakyScore", "Flaky score", existing.getFlakyScore(), incoming.getFlakyScore());
        addStepDifferences(differences, existing.getSteps(), incoming.getSteps());
        return differences;
    }

    private void addDifference(List<FieldDifference> differences,
                               String fieldKey,
                               String displayName,
                               String existingValue,
                               String incomingValue) {
        if (!normalize(existingValue).equals(normalize(incomingValue))) {
            differences.add(new FieldDifference(fieldKey, displayName, safe(existingValue), safe(incomingValue)));
        }
    }

    private void addStepDifferences(List<FieldDifference> differences,
                                    List<ReviewStepSnapshot> existingSteps,
                                    List<ReviewStepSnapshot> incomingSteps) {
        int max = Math.max(existingSteps == null ? 0 : existingSteps.size(), incomingSteps == null ? 0 : incomingSteps.size());
        for (int index = 0; index < max; index++) {
            ReviewStepSnapshot existingStep = index < (existingSteps == null ? 0 : existingSteps.size()) ? existingSteps.get(index) : null;
            ReviewStepSnapshot incomingStep = index < (incomingSteps == null ? 0 : incomingSteps.size()) ? incomingSteps.get(index) : null;

            String existingFormatted = formatStep(existingStep);
            String incomingFormatted = formatStep(incomingStep);
            if (!normalize(existingFormatted).equals(normalize(incomingFormatted))) {
                differences.add(new FieldDifference(
                    "step_" + (index + 1),
                    "Step " + (index + 1),
                    existingFormatted,
                    incomingFormatted
                ));
            }
        }
    }

    private String formatStep(ReviewStepSnapshot step) {
        if (step == null) {
            return "";
        }
        return "Action: " + safe(step.getStepSummary())
            + "\nData: " + safe(step.getTestData())
            + "\nExpected: " + safe(step.getExpectedResult());
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
