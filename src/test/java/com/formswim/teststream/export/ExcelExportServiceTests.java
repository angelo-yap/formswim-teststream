package com.formswim.teststream.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.formswim.teststream.export.services.ExcelExportService;
import com.formswim.teststream.ingestion.services.ExcelParserService;
import com.formswim.teststream.shared.domain.TestCase;
import com.formswim.teststream.shared.domain.TestStep;

class ExcelExportServiceTests {

    private final ExcelExportService excelExportService = new ExcelExportService(null);

    @Test
    void exportWorkbookUsesCanonicalHeadersAndFlattensSelectedCases() throws IOException {
        TestCase first = new TestCase(
                "TEAM1",
                "TC-101",
                "Login works",
                "Login description",
                "User exists",
                "Draft",
                "High",
                "Alice",
                "Bob",
                "5m",
                "auth",
                "API",
                "Sprint 5",
                "1.0",
                "V1",
                "Auth/Login",
                "Regression",
                "creator@example.com",
                "2026-03-01",
                "editor@example.com",
                "2026-03-02",
                "ST-1",
                "No",
                "2"
        );
        first.addStep(new TestStep(2, "Submit credentials", "user/pass", "Dashboard opens"));
        first.addStep(new TestStep(1, "Open login page", "", "Login form is visible"));

        TestCase second = new TestCase(
            "TEAM1",
                "TC-202",
                "Reset password",
                "Reset description",
                "Mailbox reachable",
                "Pass",
                "Medium",
                "Cara",
                "Dan",
                "3m",
                "recovery",
                "UI",
                "Sprint 6",
                "1.1",
                "V2",
                "Auth/Recovery",
                "Smoke",
                "creator2@example.com",
                "2026-03-03",
                "editor2@example.com",
                "2026-03-04",
                "ST-2",
                "Yes",
                "1"
        );

        byte[] workbookBytes = excelExportService.exportWorkbook(List.of(first, second));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(workbookBytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);

            Row headerRow = workbook.getSheetAt(0).getRow(0);
            assertThat(headerRow.getPhysicalNumberOfCells()).isEqualTo(ExcelParserService.CANONICAL_HEADERS.size());
            for (int columnIndex = 0; columnIndex < ExcelParserService.CANONICAL_HEADERS.size(); columnIndex++) {
                assertThat(headerRow.getCell(columnIndex).getStringCellValue())
                        .isEqualTo(ExcelParserService.CANONICAL_HEADERS.get(columnIndex));
            }

            assertThat(workbook.getSheetAt(0).getLastRowNum()).isEqualTo(3);

            Row firstRow = workbook.getSheetAt(0).getRow(1);
            assertThat(firstRow.getCell(0).getStringCellValue()).isEqualTo("TC-101");
            assertThat(firstRow.getCell(13).getStringCellValue()).isEqualTo("Open login page");
            assertThat(firstRow.getCell(15).getStringCellValue()).isEqualTo("Login form is visible");

            Row secondRow = workbook.getSheetAt(0).getRow(2);
            assertThat(secondRow.getCell(0).getStringCellValue()).isEmpty();
            assertThat(secondRow.getCell(13).getStringCellValue()).isEqualTo("Submit credentials");
            assertThat(secondRow.getCell(14).getStringCellValue()).isEqualTo("user/pass");

            Row thirdRow = workbook.getSheetAt(0).getRow(3);
            assertThat(thirdRow.getCell(0).getStringCellValue()).isEqualTo("TC-202");
            assertThat(thirdRow.getCell(13).getStringCellValue()).isEmpty();
            assertThat(thirdRow.getCell(17).getStringCellValue()).isEqualTo("Auth/Recovery");
        }
    }
}