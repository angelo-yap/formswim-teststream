package com.formswim.teststream.etl.service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.formswim.teststream.etl.dto.EtlResultSummary;
import com.formswim.teststream.etl.model.TestCase;
import com.formswim.teststream.etl.model.TestStep;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;

/**
 * Extract & Transform – QMetry .csv export format.
 *
 * Grouping rules (same as ExcelParserService):
 *   - A non-empty "Work Key" cell starts a new TestCase.
 *   - Rows with a blank "Work Key" continue the current TestCase.
 *   - A step is appended whenever "Step Summary" is non-empty.
 */
@Service
public class CsvParserService {

    private static final String COL_WORK_KEY         = "work key";
    private static final String COL_SUMMARY          = "summary";
    private static final String COL_DESCRIPTION      = "description";
    private static final String COL_PRECONDITION     = "precondition";
    private static final String COL_STATUS           = "status";
    private static final String COL_PRIORITY         = "priority";
    private static final String COL_ASSIGNEE         = "assignee";
    private static final String COL_REPORTER         = "reporter";
    private static final String COL_ESTIMATED_TIME   = "estimated time";
    private static final String COL_LABELS           = "labels";
    private static final String COL_COMPONENTS       = "components";
    private static final String COL_SPRINT           = "sprint";
    private static final String COL_FIX_VERSIONS     = "fix versions";
    private static final String COL_STEP_SUMMARY     = "step summary";
    private static final String COL_TEST_DATA        = "test data";
    private static final String COL_EXPECTED         = "expected result";
    private static final String COL_VERSION          = "version";
    private static final String COL_FOLDER           = "folder";
    private static final String COL_TEST_CASE_TYPE   = "testcase type";
    private static final String COL_CREATED_BY       = "created by";
    private static final String COL_CREATED_ON       = "created on";
    private static final String COL_UPDATED_BY       = "updated by";
    private static final String COL_UPDATED_ON       = "updated on";
    private static final String COL_STORY_LINKAGES   = "story linkages";
    private static final String COL_IS_SHARABLE_STEP = "is shareable step";
    private static final String COL_FLAKY_SCORE      = "flaky score";

    public EtlResultSummary parse(MultipartFile file, String teamKey) {
        List<String> errors = new ArrayList<>();
        List<TestCase> testCases = new ArrayList<>();

        if (teamKey == null || teamKey.isBlank()) {
            errors.add("A valid team is required.");
            return new EtlResultSummary(0, 0, errors, testCases);
        }

        try (CSVReader csvReader = new CSVReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            List<String[]> allRows = csvReader.readAll();

            if (allRows.isEmpty()) {
                errors.add("The uploaded CSV file is empty.");
                return new EtlResultSummary(0, 0, errors, testCases);
            }

            Map<String, Integer> idx = buildColumnIndex(allRows.get(0));

            Integer workKeyCol        = idx.get(COL_WORK_KEY);
            Integer summaryCol        = idx.get(COL_SUMMARY);
            Integer descriptionCol    = idx.get(COL_DESCRIPTION);
            Integer precondCol        = idx.get(COL_PRECONDITION);
            Integer statusCol         = idx.get(COL_STATUS);
            Integer priorityCol       = idx.get(COL_PRIORITY);
            Integer assigneeCol       = idx.get(COL_ASSIGNEE);
            Integer reporterCol       = idx.get(COL_REPORTER);
            Integer estimatedTimeCol  = idx.get(COL_ESTIMATED_TIME);
            Integer labelsCol         = idx.get(COL_LABELS);
            Integer componentsCol     = idx.get(COL_COMPONENTS);
            Integer sprintCol         = idx.get(COL_SPRINT);
            Integer fixVersionsCol    = idx.get(COL_FIX_VERSIONS);
            Integer stepSummaryCol    = idx.get(COL_STEP_SUMMARY);
            Integer testDataCol       = idx.get(COL_TEST_DATA);
            Integer expectedCol       = idx.get(COL_EXPECTED);
            Integer versionCol        = idx.get(COL_VERSION);
            Integer folderCol         = idx.get(COL_FOLDER);
            Integer testCaseTypeCol   = idx.get(COL_TEST_CASE_TYPE);
            Integer createdByCol      = idx.get(COL_CREATED_BY);
            Integer createdOnCol      = idx.get(COL_CREATED_ON);
            Integer updatedByCol      = idx.get(COL_UPDATED_BY);
            Integer updatedOnCol      = idx.get(COL_UPDATED_ON);
            Integer storyLinkagesCol  = idx.get(COL_STORY_LINKAGES);
            Integer isSharableStepCol = idx.get(COL_IS_SHARABLE_STEP);
            Integer flakyScoreCol     = idx.get(COL_FLAKY_SCORE);

            if (workKeyCol == null) {
                errors.add("Could not find 'Work Key' column. Check the CSV header row.");
                return new EtlResultSummary(0, 0, errors, testCases);
            }
            if (stepSummaryCol == null) {
                errors.add("Could not find 'Step Summary' column. Check the CSV header row.");
                return new EtlResultSummary(0, 0, errors, testCases);
            }

            TestCase current = null;

            for (int r = 1; r < allRows.size(); r++) {
                String[] row = allRows.get(r);
                String workKey = get(row, workKeyCol).trim();

                // Some QMetry exports repeat Work Key on every step row.
                // Treat a repeated Work Key matching the current test case as a continuation row.
                boolean startsNewCase = !workKey.isEmpty() && (current == null || !workKey.equalsIgnoreCase(current.getWorkKey()));

                if (startsNewCase) {
                    current = new TestCase(
                            teamKey,
                            workKey,
                            get(row, summaryCol),
                            get(row, descriptionCol),
                            get(row, precondCol),
                            get(row, statusCol),
                            get(row, priorityCol),
                            get(row, assigneeCol),
                            get(row, reporterCol),
                            get(row, estimatedTimeCol),
                            get(row, labelsCol),
                            get(row, componentsCol),
                            get(row, sprintCol),
                            get(row, fixVersionsCol),
                            get(row, versionCol),
                            get(row, folderCol),
                            get(row, testCaseTypeCol),
                            get(row, createdByCol),
                            get(row, createdOnCol),
                            get(row, updatedByCol),
                            get(row, updatedOnCol),
                            get(row, storyLinkagesCol),
                            get(row, isSharableStepCol),
                            get(row, flakyScoreCol)
                    );
                    testCases.add(current);
                }

                if (current != null) {
                    String stepSummary = get(row, stepSummaryCol).trim();
                    if (!stepSummary.isEmpty()) {
                        int stepNumber = current.getSteps().size() + 1;
                        current.addStep(new TestStep(
                                stepNumber,
                                stepSummary,
                                get(row, testDataCol),
                                get(row, expectedCol)
                        ));
                    }
                }
            }

            int totalSteps = testCases.stream().mapToInt(tc -> tc.getSteps().size()).sum();
            return new EtlResultSummary(testCases.size(), totalSteps, errors, testCases);

        } catch (IOException | CsvException e) {
            errors.add("Failed to read CSV: " + e.getMessage());
            return new EtlResultSummary(0, 0, errors, testCases);
        }
    }

    private Map<String, Integer> buildColumnIndex(String[] headerRow) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < headerRow.length; i++) {
            String name = headerRow[i] == null ? "" : headerRow[i].trim().toLowerCase();
            if (!name.isEmpty()) map.put(name, i);
        }
        return map;
    }

    private String get(String[] row, Integer col) {
        // Also Silently ignore missing columns???
        if (col == null || col >= row.length) return "";
        return row[col] == null ? "" : row[col].trim();
    }
}
