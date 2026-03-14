package com.formswim.teststream.etl.service;

import com.formswim.teststream.etl.dto.EtlResultSummary;
import com.formswim.teststream.etl.model.TestCase;
import com.formswim.teststream.etl.model.TestStep;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

/**
 * Extract & Transform – QMetry .xlsx export format.
 *
 * Column layout (strict, matches QMetry CSV export):
 *  0  Work Key       | 1  Summary         | 2  Description  | 3  Precondition
 *  4  Status         | 5  Priority        | 6  Assignee     | 7  Reporter
 *  8  Estimated Time | 9  Labels          | 10 Components   | 11 Sprint
 *  12 Fix Versions   | 13 Step Summary    | 14 Test Data    | 15 Expected Result
 *  16 Version        | 17 Folder          | ...
 *
 * Grouping rules:
 *  - A non-empty "Work Key" cell starts a new TestCase.
 *  - Rows with a blank "Work Key" continue the current TestCase.
 *  - A step is appended whenever "Step Summary" is non-empty.
 */
@Service
public class ExcelParserService {

    public static final List<String> CANONICAL_HEADERS = List.of(
            "Work Key",
            "Summary",
            "Description",
            "Precondition",
            "Status",
            "Priority",
            "Assignee",
            "Reporter",
            "Estimated Time",
            "Labels",
            "Components",
            "Sprint",
            "Fix Versions",
            "Step Summary",
            "Test Data",
            "Expected Result",
            "Version",
            "Folder",
            "Testcase Type",
            "Created By",
            "Created On",
            "Updated By",
            "Updated On",
            "Story Linkages",
            "Is Shareable Step",
            "Flaky Score"
    );

    // Exact header names from the QMetry export (case-insensitive lookup)
    private static final String COL_WORK_KEY = "work key";
    private static final String COL_SUMMARY = "summary";
    private static final String COL_DESCRIPTION = "description";
    private static final String COL_PRECONDITION = "precondition";
    private static final String COL_STATUS = "status";
    private static final String COL_PRIORITY = "priority";
    private static final String COL_ASSIGNEE = "assignee";
    private static final String COL_REPORTER = "reporter";
    private static final String COL_ESTIMATED_TIME = "estimated time";
    private static final String COL_LABELS = "labels";
    private static final String COL_COMPONENTS = "components";
    private static final String COL_SPRINT = "sprint";
    private static final String COL_FIX_VERSIONS = "fix versions";
    private static final String COL_STEP_SUMMARY = "step summary";
    private static final String COL_TEST_DATA = "test data";
    private static final String COL_EXPECTED = "expected result";
    private static final String COL_VERSION = "version";
    private static final String COL_FOLDER = "folder";
    private static final String COL_TEST_CASE_TYPE = "testcase type";
    private static final String COL_CREATED_BY = "created by";
    private static final String COL_CREATED_ON = "created on";
    private static final String COL_UPDATED_BY = "updated by";
    private static final String COL_UPDATED_ON = "updated on";
    private static final String COL_STORY_LINKAGES = "story linkages";
    private static final String COL_IS_SHARABLE_STEP = "is shareable step";
    private static final String COL_FLAKY_SCORE = "flaky score";

    public List<String> canonicalHeaders() {
        return CANONICAL_HEADERS;
    }

    public EtlResultSummary parse(MultipartFile file, String teamKey) {
        List<String> errors = new ArrayList<>();
        List<TestCase> testCases = new ArrayList<>();

        if (teamKey == null || teamKey.isBlank()) {
            errors.add("A valid team is required.");
            return new EtlResultSummary(0, 0, errors, testCases);
        }

        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (!filename.endsWith(".xlsx")) {
            errors.add("Only .xlsx files are supported. Received: " + filename);
            return new EtlResultSummary(0, 0, errors, testCases);
        }

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIter = sheet.iterator();

            if (!rowIter.hasNext()) {
                errors.add("The uploaded file is empty.");
                return new EtlResultSummary(0, 0, errors, testCases);
            }

            // --- Map exact header names to column indices ---
            Row headerRow = rowIter.next();
            Map<String, Integer> idx = buildColumnIndex(headerRow);

            Integer workKeyCol = idx.get(COL_WORK_KEY);
            Integer summaryCol = idx.get(COL_SUMMARY);
            Integer descriptionCol = idx.get(COL_DESCRIPTION);
            Integer precondCol = idx.get(COL_PRECONDITION);
            Integer statusCol = idx.get(COL_STATUS);
            Integer priorityCol = idx.get(COL_PRIORITY);
            Integer assigneeCol = idx.get(COL_ASSIGNEE);
            Integer reporterCol = idx.get(COL_REPORTER);
            Integer estimatedTimeCol = idx.get(COL_ESTIMATED_TIME);
            Integer labelsCol = idx.get(COL_LABELS);
            Integer componentsCol = idx.get(COL_COMPONENTS);
            Integer sprintCol = idx.get(COL_SPRINT);
            Integer fixVersionsCol = idx.get(COL_FIX_VERSIONS);
            Integer stepSummaryCol = idx.get(COL_STEP_SUMMARY);
            Integer testDataCol = idx.get(COL_TEST_DATA);
            Integer expectedCol = idx.get(COL_EXPECTED);
            Integer versionCol = idx.get(COL_VERSION);
            Integer folderCol = idx.get(COL_FOLDER);
            Integer testCaseTypeCol = idx.get(COL_TEST_CASE_TYPE);
            Integer createdByCol = idx.get(COL_CREATED_BY);
            Integer createdOnCol = idx.get(COL_CREATED_ON);
            Integer updatedByCol = idx.get(COL_UPDATED_BY);
            Integer updatedOnCol = idx.get(COL_UPDATED_ON);
            Integer storyLinkagesCol = idx.get(COL_STORY_LINKAGES);
            Integer isSharableStepCol = idx.get(COL_IS_SHARABLE_STEP);
            Integer flakyScoreCol = idx.get(COL_FLAKY_SCORE);

            if (workKeyCol == null) {
                errors.add("Could not find 'Work Key' column. Check the header row.");
                return new EtlResultSummary(0, 0, errors, testCases);
            }
            if (stepSummaryCol == null) {
                errors.add("Could not find 'Step Summary' column. Check the header row.");
                return new EtlResultSummary(0, 0, errors, testCases);
            }

            // --- Parse data rows ---
            TestCase current = null;

            while (rowIter.hasNext()) {
                Row row = rowIter.next();

                String workKey = cell(row, workKeyCol).trim();

                // A non-empty Work Key starts a new TestCase
                if (!workKey.isEmpty()) {
                    current = new TestCase(
                            teamKey,
                            workKey,
                            cell(row, summaryCol),
                            cell(row, descriptionCol),
                            cell(row, precondCol),
                            cell(row, statusCol),
                            cell(row, priorityCol),
                            cell(row, assigneeCol),
                            cell(row, reporterCol),
                            cell(row, estimatedTimeCol),
                            cell(row, labelsCol),
                            cell(row, componentsCol),
                            cell(row, sprintCol),
                            cell(row, fixVersionsCol),
                            cell(row, versionCol),
                            cell(row, folderCol),
                            cell(row, testCaseTypeCol),
                            cell(row, createdByCol),
                            cell(row, createdOnCol),
                            cell(row, updatedByCol),
                            cell(row, updatedOnCol),
                            cell(row, storyLinkagesCol),
                            cell(row, isSharableStepCol),
                            cell(row, flakyScoreCol)
                    );
                    testCases.add(current);
                }

                // Add step whenever Step Summary is non-empty
                if (current != null) {
                    String stepSummary = cell(row, stepSummaryCol).trim();
                    if (!stepSummary.isEmpty()) {
                        int stepNumber = current.getSteps().size() + 1;
                        current.addStep(new TestStep(
                                stepNumber,
                                stepSummary,
                                cell(row, testDataCol),
                                cell(row, expectedCol)
                        ));
                    }
                }
            }

            int totalSteps = testCases.stream().mapToInt(tc -> tc.getSteps().size()).sum();
            return new EtlResultSummary(testCases.size(), totalSteps, errors, testCases);

        } catch (IOException e) {
            errors.add("Failed to read file: " + e.getMessage());
            return new EtlResultSummary(0, 0, errors, testCases);
        }
    }

    // ---- Helpers ----

    /** Build a lowercase header-name → column-index map from the header row. */
    private Map<String, Integer> buildColumnIndex(Row header) {
        Map<String, Integer> map = new HashMap<>();
        for (Cell cell : header) {
            String name = cellString(cell).trim().toLowerCase();
            if (!name.isEmpty()) map.put(name, cell.getColumnIndex());
        }
        return map;
    }

    /** Safe cell read: returns empty string for null/missing cells. */
    private String cell(Row row, Integer col) {
        // Silently ignore missing columns???
        if (col == null) return "";
        Cell c = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        return c == null ? "" : cellString(c);
    }

    private String cellString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getDateCellValue().toString()
                    : String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCachedFormulaResultType() == CellType.STRING
                    ? cell.getStringCellValue()
                    : String.valueOf((long) cell.getNumericCellValue());
            default      -> "";
        };
    }
}