package com.formswim.teststream.etl.service;

import com.formswim.teststream.shared.domain.TestCase;
import com.formswim.teststream.shared.domain.TestStep;
import com.formswim.teststream.shared.domain.TestCaseRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExcelExportService {

    private static final String SHEET_NAME = "Test Cases";
    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    private final TestCaseRepository testCaseRepository;

    public ExcelExportService(TestCaseRepository testCaseRepository) {
        this.testCaseRepository = testCaseRepository;
    }

    public List<TestCase> getAllTestCasesForExport(String teamKey) {
        return testCaseRepository.findAllWithStepsByTeamKey(teamKey);
    }

    public List<TestCase> getSelectedTestCasesForExport(String teamKey, Collection<String> requestedWorkKeys) {
        if (requestedWorkKeys == null || requestedWorkKeys.isEmpty()) {
            return getAllTestCasesForExport(teamKey);
        }

        List<String> normalizedWorkKeys = requestedWorkKeys.stream()
                .filter(workKey -> workKey != null && !workKey.isBlank())
                .distinct()
                .toList();

        if (normalizedWorkKeys.isEmpty()) {
            return getAllTestCasesForExport(teamKey);
        }

        long scopedMatches = testCaseRepository.countByTeamKeyAndWorkKeyIn(teamKey, normalizedWorkKeys);
        long totalMatches = testCaseRepository.countByWorkKeyIn(normalizedWorkKeys);
        if (totalMatches > scopedMatches) {
            throw new IllegalArgumentException("Requested test cases include work keys outside your team.");
        }

        List<TestCase> testCases = testCaseRepository.findAllWithStepsByTeamKeyAndWorkKeyIn(teamKey, normalizedWorkKeys);
        Map<String, TestCase> byWorkKey = new LinkedHashMap<>();
        for (TestCase testCase : testCases) {
            byWorkKey.put(testCase.getWorkKey(), testCase);
        }

        List<TestCase> ordered = new ArrayList<>();
        for (String workKey : normalizedWorkKeys) {
            TestCase testCase = byWorkKey.get(workKey);
            if (testCase != null) {
                ordered.add(testCase);
            }
        }
        return ordered;
    }

    public ResponseEntity<ByteArrayResource> buildDownloadResponse(List<TestCase> testCases, String filename) {
        byte[] workbook = exportWorkbook(testCases);
        ByteArrayResource resource = new ByteArrayResource(workbook);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(XLSX_MEDIA_TYPE)
                .contentLength(workbook.length)
                .body(resource);
    }

    public byte[] exportWorkbook(List<TestCase> testCases) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(SHEET_NAME);
            createHeaderRow(workbook, sheet);

            int rowIndex = 1;
            for (TestCase testCase : testCases) {
                List<TestStep> orderedSteps = orderedSteps(testCase);
                if (orderedSteps.isEmpty()) {
                    writeDataRow(sheet.createRow(rowIndex++), testCase, null, true);
                    continue;
                }

                for (int stepIndex = 0; stepIndex < orderedSteps.size(); stepIndex++) {
                    writeDataRow(sheet.createRow(rowIndex++), testCase, orderedSteps.get(stepIndex), stepIndex == 0);
                }
            }

            for (int columnIndex = 0; columnIndex < ExcelParserService.CANONICAL_HEADERS.size(); columnIndex++) {
                sheet.autoSizeColumn(columnIndex);
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to generate Excel workbook", exception);
        }
    }

    private void createHeaderRow(Workbook workbook, Sheet sheet) {
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);

        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFont(headerFont);

        Row headerRow = sheet.createRow(0);
        for (int columnIndex = 0; columnIndex < ExcelParserService.CANONICAL_HEADERS.size(); columnIndex++) {
            Cell cell = headerRow.createCell(columnIndex);
            cell.setCellValue(ExcelParserService.CANONICAL_HEADERS.get(columnIndex));
            cell.setCellStyle(headerStyle);
        }
    }

    private void writeDataRow(Row row, TestCase testCase, TestStep testStep, boolean includeCaseFields) {
        List<String> values = new ArrayList<>(ExcelParserService.CANONICAL_HEADERS.size());
        appendCaseFields(values, testCase, includeCaseFields);
        values.add(testStep == null ? "" : safe(testStep.getStepSummary()));
        values.add(testStep == null ? "" : safe(testStep.getTestData()));
        values.add(testStep == null ? "" : safe(testStep.getExpectedResult()));
        appendTrailingCaseFields(values, testCase, includeCaseFields);

        for (int columnIndex = 0; columnIndex < values.size(); columnIndex++) {
            row.createCell(columnIndex).setCellValue(values.get(columnIndex));
        }
    }

    private void appendCaseFields(List<String> values, TestCase testCase, boolean includeCaseFields) {
        if (!includeCaseFields) {
            for (int index = 0; index < 13; index++) {
                values.add("");
            }
            return;
        }

        values.add(safe(testCase.getWorkKey()));
        values.add(safe(testCase.getSummary()));
        values.add(safe(testCase.getDescription()));
        values.add(safe(testCase.getPrecondition()));
        values.add(safe(testCase.getStatus()));
        values.add(safe(testCase.getPriority()));
        values.add(safe(testCase.getAssignee()));
        values.add(safe(testCase.getReporter()));
        values.add(safe(testCase.getEstimatedTime()));
        values.add(safe(testCase.getLabels()));
        values.add(safe(testCase.getComponents()));
        values.add(safe(testCase.getSprint()));
        values.add(safe(testCase.getFixVersions()));
    }

    private void appendTrailingCaseFields(List<String> values, TestCase testCase, boolean includeCaseFields) {
        if (!includeCaseFields) {
            for (int index = 0; index < 10; index++) {
                values.add("");
            }
            return;
        }

        values.add(safe(testCase.getVersion()));
        values.add(safe(testCase.getFolder()));
        values.add(safe(testCase.getTestCaseType()));
        values.add(safe(testCase.getCreatedBy()));
        values.add(safe(testCase.getCreatedOn()));
        values.add(safe(testCase.getUpdatedBy()));
        values.add(safe(testCase.getUpdatedOn()));
        values.add(safe(testCase.getStoryLinkages()));
        values.add(safe(testCase.getIsSharableStep()));
        values.add(safe(testCase.getFlakyScore()));
    }

    private List<TestStep> orderedSteps(TestCase testCase) {
        return testCase.getSteps().stream()
                .sorted(Comparator.comparingInt(TestStep::getStepNumber))
                .toList();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
