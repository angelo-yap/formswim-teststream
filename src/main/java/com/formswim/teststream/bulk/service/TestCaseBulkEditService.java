package com.formswim.teststream.bulk.service;

import com.formswim.teststream.bulk.dto.BulkEditRequest;
import com.formswim.teststream.bulk.dto.BulkEditResult;
import com.formswim.teststream.shared.domain.TestCase;
import com.formswim.teststream.shared.domain.TestStep;
import com.formswim.teststream.shared.domain.TestCaseRepository;
import com.formswim.teststream.workspace.services.FolderPathSyncService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
/**
 * Service for bulk text replacement and fixed-status assignment across test cases and child steps.
 *
 * <p>The public mutation method is transactional so partial writes are rolled back
 * if any persistence error occurs during processing.</p>
 */
public class TestCaseBulkEditService {

    private static final String FAILURE_INVALID = "INVALID_WORK_KEY";
    private static final String FAILURE_FORBIDDEN = "FORBIDDEN";
    private static final String FAILURE_NOT_FOUND = "NOT_FOUND";
    private static final Set<String> ALLOWED_STATUS_VALUES = Set.of("Done", "Draft", "Pass", "Fail");

    private static final Set<String> SUPPORTED_FIELDS = Set.of(
        "summary",
        "description",
        "precondition",
        "priority",
        "assignee",
        "reporter",
        "estimatedTime",
        "labels",
        "components",
        "sprint",
        "fixVersions",
        "version",
        "folder",
        "testCaseType",
        "createdBy",
        "createdOn",
        "updatedBy",
        "storyLinkages",
        "isSharableStep",
        "flakyScore",
        "stepSummary",
        "testData",
        "expectedResult"
    );

    private static final Set<String> DIRECT_ASSIGNABLE_FIELDS = Set.of(
        "summary",
        "description",
        "precondition",
        "priority",
        "components",
        "sprint",
        "fixVersions",
        "version",
        "folder",
        "testCaseType",
        "labels",
        "estimatedTime",
        "storyLinkages"
    );

    private static final Map<String, String> FIELD_ALIASES = Map.ofEntries(
        Map.entry("summary", "summary"),
        Map.entry("description", "description"),
        Map.entry("precondition", "precondition"),
        Map.entry("priority", "priority"),
        Map.entry("assignee", "assignee"),
        Map.entry("reporter", "reporter"),
        Map.entry("estimatedtime", "estimatedTime"),
        Map.entry("labels", "labels"),
        Map.entry("components", "components"),
        Map.entry("sprint", "sprint"),
        Map.entry("fixversions", "fixVersions"),
        Map.entry("version", "version"),
        Map.entry("folder", "folder"),
        Map.entry("testcasetype", "testCaseType"),
        Map.entry("createdby", "createdBy"),
        Map.entry("createdon", "createdOn"),
        Map.entry("updatedby", "updatedBy"),
        Map.entry("storylinkages", "storyLinkages"),
        Map.entry("issharablestep", "isSharableStep"),
        Map.entry("flakyscore", "flakyScore"),
        Map.entry("stepsummary", "stepSummary"),
        Map.entry("testdata", "testData"),
        Map.entry("expectedresult", "expectedResult")
    );

    private final TestCaseRepository testCaseRepository;
    private final FolderPathSyncService folderPathSyncService;
    private final int maxWorkKeys;

    public TestCaseBulkEditService(TestCaseRepository testCaseRepository,
                                   FolderPathSyncService folderPathSyncService,
                                   @Value("${teststream.bulk-edit.max-work-keys:5000}") int maxWorkKeys) {
        this.testCaseRepository = testCaseRepository;
        this.folderPathSyncService = folderPathSyncService;
        this.maxWorkKeys = maxWorkKeys;
    }

    /**
     * Applies team-scoped text replacement and optional fixed-status assignment for work keys.
     *
     * <p>Processing flow:</p>
     * <ol>
     *     <li>Validate request size and normalize work keys.</li>
     *     <li>Classify non-owned/missing keys as failures.</li>
     *     <li>Resolve and validate requested text fields (including aliases).</li>
     *     <li>Apply requested text replacements and status updates.</li>
     *     <li>Persist once; transaction rolls back if persistence fails.</li>
     * </ol>
     *
     * @param teamKey team scope used for ownership enforcement
     * @param request bulk edit request payload
     * @return structured summary and failure diagnostics
     */
    @Transactional
    public BulkEditResult bulkEditByWorkKeys(String teamKey, BulkEditRequest request) {
        BulkEditResult result = new BulkEditResult();
        List<String> requestedWorkKeys = request.getWorkKeys();
        result.setRequestedCount(requestedWorkKeys.size());

        if (requestedWorkKeys.size() > maxWorkKeys) {
            throw new IllegalArgumentException("Too many workKeys requested. Maximum allowed is " + maxWorkKeys + ".");
        }

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

        String findText = request.getFindText();
        String replaceText = request.getReplaceText();
        boolean caseSensitive = request.getCaseSensitive() == null || request.getCaseSensitive();
        String requestedStatusValue = normalizeStatusValue(request.getStatusValue());
        boolean hasTextOperation = findText != null && !findText.isEmpty();
        Set<String> requestedFields = hasTextOperation ? resolveRequestedFields(request.getFields()) : Set.of();
        Map<String, String> directFieldAssignments = resolveDirectFieldAssignments(request.getFieldValues());

        List<String> normalizedWorkKeys = new ArrayList<>(normalizedUniqueWorkKeys);
        Set<String> ownedWorkKeys = new HashSet<>(testCaseRepository.findOwnedWorkKeysIn(teamKey, normalizedWorkKeys));
        Set<String> existingWorkKeys = new HashSet<>(testCaseRepository.findExistingWorkKeysIn(normalizedWorkKeys));

        List<String> allowedWorkKeys = new ArrayList<>();
        for (String workKey : normalizedWorkKeys) {
            if (ownedWorkKeys.contains(workKey)) {
                allowedWorkKeys.add(workKey);
                continue;
            }

            if (existingWorkKeys.contains(workKey)) {
                addFailure(result, workKey, FAILURE_FORBIDDEN);
                result.setForbiddenCount(result.getForbiddenCount() + 1);
            } else {
                addFailure(result, workKey, FAILURE_NOT_FOUND);
                result.setNotFoundCount(result.getNotFoundCount() + 1);
            }
        }

        result.setCandidateCount(allowedWorkKeys.size());
        if (allowedWorkKeys.isEmpty()) {
            return result;
        }

        List<TestCase> cases = testCaseRepository.findAllWithStepsByTeamKeyAndWorkKeyIn(teamKey, allowedWorkKeys);

        int updatedCaseCount = 0;
        int updatedStepCount = 0;
        int totalReplacements = 0;
        String updatedOnValue = LocalDate.now().toString();
        Set<String> syncedFolders = new LinkedHashSet<>();

        for (TestCase testCase : cases) {
            boolean caseChanged = false;

            String originalSummary = testCase.getSummary();
            ReplaceOutcome summaryOutcome = updateIfRequested(requestedFields.contains("summary"), testCase.getSummary(), findText, replaceText, caseSensitive);
            String summaryValue = applyDirectAssignment("summary", summaryOutcome.updatedValue(), directFieldAssignments);
            testCase.setSummary(summaryValue);
            caseChanged = caseChanged || summaryOutcome.changed() || !equalsValue(originalSummary, summaryValue);
            totalReplacements += summaryOutcome.replacementCount();

            String originalDescription = testCase.getDescription();
            ReplaceOutcome descriptionOutcome = updateIfRequested(requestedFields.contains("description"), testCase.getDescription(), findText, replaceText, caseSensitive);
            String descriptionValue = applyDirectAssignment("description", descriptionOutcome.updatedValue(), directFieldAssignments);
            testCase.setDescription(descriptionValue);
            caseChanged = caseChanged || descriptionOutcome.changed() || !equalsValue(originalDescription, descriptionValue);
            totalReplacements += descriptionOutcome.replacementCount();

            String originalPrecondition = testCase.getPrecondition();
            ReplaceOutcome preconditionOutcome = updateIfRequested(requestedFields.contains("precondition"), testCase.getPrecondition(), findText, replaceText, caseSensitive);
            String preconditionValue = applyDirectAssignment("precondition", preconditionOutcome.updatedValue(), directFieldAssignments);
            testCase.setPrecondition(preconditionValue);
            caseChanged = caseChanged || preconditionOutcome.changed() || !equalsValue(originalPrecondition, preconditionValue);
            totalReplacements += preconditionOutcome.replacementCount();

            String originalPriority = testCase.getPriority();
            ReplaceOutcome priorityOutcome = updateIfRequested(requestedFields.contains("priority"), testCase.getPriority(), findText, replaceText, caseSensitive);
            String priorityValue = applyDirectAssignment("priority", priorityOutcome.updatedValue(), directFieldAssignments);
            testCase.setPriority(priorityValue);
            caseChanged = caseChanged || priorityOutcome.changed() || !equalsValue(originalPriority, priorityValue);
            totalReplacements += priorityOutcome.replacementCount();

            ReplaceOutcome assigneeOutcome = updateIfRequested(requestedFields.contains("assignee"), testCase.getAssignee(), findText, replaceText, caseSensitive);
            testCase.setAssignee(assigneeOutcome.updatedValue());
            caseChanged = caseChanged || assigneeOutcome.changed();
            totalReplacements += assigneeOutcome.replacementCount();

            ReplaceOutcome reporterOutcome = updateIfRequested(requestedFields.contains("reporter"), testCase.getReporter(), findText, replaceText, caseSensitive);
            testCase.setReporter(reporterOutcome.updatedValue());
            caseChanged = caseChanged || reporterOutcome.changed();
            totalReplacements += reporterOutcome.replacementCount();

            String originalEstimatedTime = testCase.getEstimatedTime();
            ReplaceOutcome estimatedTimeOutcome = updateIfRequested(requestedFields.contains("estimatedTime"), testCase.getEstimatedTime(), findText, replaceText, caseSensitive);
            String estimatedTimeValue = applyDirectAssignment("estimatedTime", estimatedTimeOutcome.updatedValue(), directFieldAssignments);
            testCase.setEstimatedTime(estimatedTimeValue);
            caseChanged = caseChanged || estimatedTimeOutcome.changed() || !equalsValue(originalEstimatedTime, estimatedTimeValue);
            totalReplacements += estimatedTimeOutcome.replacementCount();

            String originalLabels = testCase.getLabels();
            ReplaceOutcome labelsOutcome = updateIfRequested(requestedFields.contains("labels"), testCase.getLabels(), findText, replaceText, caseSensitive);
            String labelsValue = applyDirectAssignment("labels", labelsOutcome.updatedValue(), directFieldAssignments);
            testCase.setLabels(labelsValue);
            caseChanged = caseChanged || labelsOutcome.changed() || !equalsValue(originalLabels, labelsValue);
            totalReplacements += labelsOutcome.replacementCount();

            String originalComponents = testCase.getComponents();
            ReplaceOutcome componentsOutcome = updateIfRequested(requestedFields.contains("components"), testCase.getComponents(), findText, replaceText, caseSensitive);
            String componentsValue = applyDirectAssignment("components", componentsOutcome.updatedValue(), directFieldAssignments);
            testCase.setComponents(componentsValue);
            caseChanged = caseChanged || componentsOutcome.changed() || !equalsValue(originalComponents, componentsValue);
            totalReplacements += componentsOutcome.replacementCount();

            String originalSprint = testCase.getSprint();
            ReplaceOutcome sprintOutcome = updateIfRequested(requestedFields.contains("sprint"), testCase.getSprint(), findText, replaceText, caseSensitive);
            String sprintValue = applyDirectAssignment("sprint", sprintOutcome.updatedValue(), directFieldAssignments);
            testCase.setSprint(sprintValue);
            caseChanged = caseChanged || sprintOutcome.changed() || !equalsValue(originalSprint, sprintValue);
            totalReplacements += sprintOutcome.replacementCount();

            String originalFixVersions = testCase.getFixVersions();
            ReplaceOutcome fixVersionsOutcome = updateIfRequested(requestedFields.contains("fixVersions"), testCase.getFixVersions(), findText, replaceText, caseSensitive);
            String fixVersionsValue = applyDirectAssignment("fixVersions", fixVersionsOutcome.updatedValue(), directFieldAssignments);
            testCase.setFixVersions(fixVersionsValue);
            caseChanged = caseChanged || fixVersionsOutcome.changed() || !equalsValue(originalFixVersions, fixVersionsValue);
            totalReplacements += fixVersionsOutcome.replacementCount();

            String originalVersion = testCase.getVersion();
            ReplaceOutcome versionOutcome = updateIfRequested(requestedFields.contains("version"), testCase.getVersion(), findText, replaceText, caseSensitive);
            String versionValue = applyDirectAssignment("version", versionOutcome.updatedValue(), directFieldAssignments);
            testCase.setVersion(versionValue);
            caseChanged = caseChanged || versionOutcome.changed() || !equalsValue(originalVersion, versionValue);
            totalReplacements += versionOutcome.replacementCount();

            ReplaceOutcome folderOutcome = updateIfRequested(requestedFields.contains("folder"), testCase.getFolder(), findText, replaceText, caseSensitive);
            String originalFolder = testCase.getFolder();
            String folderValue = applyDirectAssignment("folder", folderOutcome.updatedValue(), directFieldAssignments);
            testCase.setFolder(folderValue);
            boolean folderChanged = !equalsValue(originalFolder, folderValue);
            caseChanged = caseChanged || folderOutcome.changed() || folderChanged;
            totalReplacements += folderOutcome.replacementCount();
            if (folderChanged && folderValue != null && !folderValue.isBlank()) {
                syncedFolders.add(folderValue);
            }

            String originalTestCaseType = testCase.getTestCaseType();
            ReplaceOutcome testCaseTypeOutcome = updateIfRequested(requestedFields.contains("testCaseType"), testCase.getTestCaseType(), findText, replaceText, caseSensitive);
            String testCaseTypeValue = applyDirectAssignment("testCaseType", testCaseTypeOutcome.updatedValue(), directFieldAssignments);
            testCase.setTestCaseType(testCaseTypeValue);
            caseChanged = caseChanged || testCaseTypeOutcome.changed() || !equalsValue(originalTestCaseType, testCaseTypeValue);
            totalReplacements += testCaseTypeOutcome.replacementCount();

            ReplaceOutcome createdByOutcome = updateIfRequested(requestedFields.contains("createdBy"), testCase.getCreatedBy(), findText, replaceText, caseSensitive);
            testCase.setCreatedBy(createdByOutcome.updatedValue());
            caseChanged = caseChanged || createdByOutcome.changed();
            totalReplacements += createdByOutcome.replacementCount();

            ReplaceOutcome createdOnOutcome = updateIfRequested(requestedFields.contains("createdOn"), testCase.getCreatedOn(), findText, replaceText, caseSensitive);
            testCase.setCreatedOn(createdOnOutcome.updatedValue());
            caseChanged = caseChanged || createdOnOutcome.changed();
            totalReplacements += createdOnOutcome.replacementCount();

            ReplaceOutcome updatedByOutcome = updateIfRequested(requestedFields.contains("updatedBy"), testCase.getUpdatedBy(), findText, replaceText, caseSensitive);
            testCase.setUpdatedBy(updatedByOutcome.updatedValue());
            caseChanged = caseChanged || updatedByOutcome.changed();
            totalReplacements += updatedByOutcome.replacementCount();

            String originalStoryLinkages = testCase.getStoryLinkages();
            ReplaceOutcome storyLinkagesOutcome = updateIfRequested(requestedFields.contains("storyLinkages"), testCase.getStoryLinkages(), findText, replaceText, caseSensitive);
            String storyLinkagesValue = applyDirectAssignment("storyLinkages", storyLinkagesOutcome.updatedValue(), directFieldAssignments);
            testCase.setStoryLinkages(storyLinkagesValue);
            caseChanged = caseChanged || storyLinkagesOutcome.changed() || !equalsValue(originalStoryLinkages, storyLinkagesValue);
            totalReplacements += storyLinkagesOutcome.replacementCount();

            ReplaceOutcome isSharableStepOutcome = updateIfRequested(requestedFields.contains("isSharableStep"), testCase.getIsSharableStep(), findText, replaceText, caseSensitive);
            testCase.setIsSharableStep(isSharableStepOutcome.updatedValue());
            caseChanged = caseChanged || isSharableStepOutcome.changed();
            totalReplacements += isSharableStepOutcome.replacementCount();

            ReplaceOutcome flakyScoreOutcome = updateIfRequested(requestedFields.contains("flakyScore"), testCase.getFlakyScore(), findText, replaceText, caseSensitive);
            testCase.setFlakyScore(flakyScoreOutcome.updatedValue());
            caseChanged = caseChanged || flakyScoreOutcome.changed();
            totalReplacements += flakyScoreOutcome.replacementCount();

            if (requestedStatusValue != null && !requestedStatusValue.equals(testCase.getStatus())) {
                testCase.setStatus(requestedStatusValue);
                caseChanged = true;
            }

            if (caseChanged) {
                updatedCaseCount++;
            }

            boolean anyStepChanged = false;
            for (TestStep step : testCase.getSteps()) {
                boolean currentStepChanged = false;

                ReplaceOutcome stepSummaryOutcome = updateIfRequested(requestedFields.contains("stepSummary"), step.getStepSummary(), findText, replaceText, caseSensitive);
                step.setStepSummary(stepSummaryOutcome.updatedValue());
                currentStepChanged = currentStepChanged || stepSummaryOutcome.changed();
                totalReplacements += stepSummaryOutcome.replacementCount();

                ReplaceOutcome testDataOutcome = updateIfRequested(requestedFields.contains("testData"), step.getTestData(), findText, replaceText, caseSensitive);
                step.setTestData(testDataOutcome.updatedValue());
                currentStepChanged = currentStepChanged || testDataOutcome.changed();
                totalReplacements += testDataOutcome.replacementCount();

                ReplaceOutcome expectedResultOutcome = updateIfRequested(requestedFields.contains("expectedResult"), step.getExpectedResult(), findText, replaceText, caseSensitive);
                step.setExpectedResult(expectedResultOutcome.updatedValue());
                currentStepChanged = currentStepChanged || expectedResultOutcome.changed();
                totalReplacements += expectedResultOutcome.replacementCount();

                if (currentStepChanged) {
                    updatedStepCount++;
                    anyStepChanged = true;
                }
            }

            if (caseChanged || anyStepChanged) {
                testCase.setUpdatedOn(updatedOnValue);
            }
        }

        folderPathSyncService.ensureFolderPathsExist(teamKey, syncedFolders, "bulk-edit");
        testCaseRepository.saveAll(cases);

        result.setUpdatedCaseCount(updatedCaseCount);
        result.setUpdatedStepCount(updatedStepCount);
        result.setTotalReplacements(totalReplacements);
        return result;
    }

    /**
     * Resolves requested field selectors into canonical supported names.
     *
     * <p>When caller omits fields, all supported fields are targeted.</p>
     */
    private Set<String> resolveRequestedFields(List<String> rawFields) {
        if (rawFields == null || rawFields.isEmpty()) {
            return SUPPORTED_FIELDS;
        }

        LinkedHashSet<String> resolved = new LinkedHashSet<>();
        for (String rawField : rawFields) {
            if (rawField == null) {
                continue;
            }
            String normalized = rawField.trim();
            if (normalized.isEmpty()) {
                continue;
            }
            String canonicalField = toCanonicalField(normalized);
            if (canonicalField == null || !SUPPORTED_FIELDS.contains(canonicalField)) {
                throw new IllegalArgumentException("Unsupported field: " + normalized);
            }
            resolved.add(canonicalField);
        }
        if (resolved.isEmpty()) {
            throw new IllegalArgumentException("At least one valid field is required");
        }
        return resolved;
    }

    /**
     * Converts UI-friendly aliases (snake-case, kebab-case, lowercase) into canonical field names.
     */
    private String toCanonicalField(String fieldName) {
        String key = fieldName.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
        return FIELD_ALIASES.get(key);
    }

    /**
     * Performs one literal replacement operation for a field when it is requested.
     */
    private ReplaceOutcome updateIfRequested(boolean shouldEdit, String source, String findText, String replaceText, boolean caseSensitive) {
        if (!shouldEdit || source == null || findText == null || findText.isEmpty()) {
            return new ReplaceOutcome(source, 0, false);
        }

        if (caseSensitive) {
            int occurrences = countOccurrences(source, findText);
            if (occurrences == 0) {
                return new ReplaceOutcome(source, 0, false);
            }

            String updated = source.replace(findText, replaceText == null ? "" : replaceText);
            return new ReplaceOutcome(updated, occurrences, true);
        }

        return replaceLiteralIgnoreCase(source, findText, replaceText);
    }

    /**
     * Counts non-overlapping token occurrences to keep replacement metrics accurate.
     */
    private int countOccurrences(String source, String token) {
        if (source == null || token == null || token.isEmpty()) {
            return 0;
        }

        int index = 0;
        int count = 0;
        while (true) {
            int found = source.indexOf(token, index);
            if (found < 0) {
                return count;
            }
            count++;
            index = found + token.length();
        }
    }

    private ReplaceOutcome replaceLiteralIgnoreCase(String source, String findText, String replaceText) {
        String needle = findText.toLowerCase(Locale.ROOT);
        String haystack = source.toLowerCase(Locale.ROOT);
        int searchIndex = 0;
        int occurrences = 0;
        int matchIndex;
        String replacement = replaceText == null ? "" : replaceText;
        StringBuilder builder = new StringBuilder(source.length());

        while ((matchIndex = haystack.indexOf(needle, searchIndex)) >= 0) {
            builder.append(source, searchIndex, matchIndex);
            builder.append(replacement);
            searchIndex = matchIndex + findText.length();
            occurrences++;
        }

        if (occurrences == 0) {
            return new ReplaceOutcome(source, 0, false);
        }

        builder.append(source, searchIndex, source.length());
        return new ReplaceOutcome(builder.toString(), occurrences, true);
    }

    private String normalizeStatusValue(String rawStatusValue) {
        if (rawStatusValue == null) {
            return null;
        }

        String trimmed = rawStatusValue.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        if (!ALLOWED_STATUS_VALUES.contains(trimmed)) {
            throw new IllegalArgumentException("Unsupported status: " + trimmed);
        }

        return trimmed;
    }

    private Map<String, String> resolveDirectFieldAssignments(Map<String, String> rawFieldValues) {
        if (rawFieldValues == null || rawFieldValues.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> resolved = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, String> entry : rawFieldValues.entrySet()) {
            String rawField = entry.getKey();
            if (rawField == null) {
                continue;
            }

            String normalized = rawField.trim();
            if (normalized.isEmpty()) {
                continue;
            }

            String canonicalField = toCanonicalField(normalized);
            if (canonicalField == null || !DIRECT_ASSIGNABLE_FIELDS.contains(canonicalField)) {
                throw new IllegalArgumentException("Unsupported direct field: " + normalized);
            }

            resolved.put(canonicalField, entry.getValue());
        }

        if (resolved.isEmpty()) {
            throw new IllegalArgumentException("At least one valid direct field is required");
        }

        return resolved;
    }

    private String applyDirectAssignment(String fieldKey, String currentValue, Map<String, String> directFieldAssignments) {
        if (!directFieldAssignments.containsKey(fieldKey)) {
            return currentValue;
        }
        return directFieldAssignments.get(fieldKey);
    }

    private boolean equalsValue(String left, String right) {
        return Objects.equals(left, right);
    }

    /**
     * Appends one classified failure entry into the response payload.
     */
    private void addFailure(BulkEditResult result, String workKey, String reason) {
        result.getFailures().add(new BulkEditResult.BulkEditFailure(workKey, reason));
    }

    private record ReplaceOutcome(String updatedValue, int replacementCount, boolean changed) {
    }
}
