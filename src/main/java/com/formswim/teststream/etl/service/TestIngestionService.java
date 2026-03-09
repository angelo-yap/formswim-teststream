package com.formswim.teststream.etl.service;
// Transactional creater (Load & Hash Check)

// Potentiall should be moved ot test?

import com.formswim.teststream.etl.dto.EtlResultSummary;
import com.formswim.teststream.etl.model.TestCase;
import com.formswim.teststream.etl.repository.TestCaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@Service
public class TestIngestionService {

    private final ExcelParserService excelParserService;
    private final CsvParserService csvParserService;
    private final TestCaseRepository testCaseRepository;

    public TestIngestionService(ExcelParserService excelParserService,
            CsvParserService csvParserService,
            TestCaseRepository testCaseRepository) {
        this.excelParserService = excelParserService;
        this.csvParserService = csvParserService;
        this.testCaseRepository = testCaseRepository;
    }

    public EtlResultSummary ingestFile(MultipartFile file) {
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();

        if (file.isEmpty()) {
            return new EtlResultSummary(0, 0, List.of("File is empty."), List.of());
        }
        if (!filename.endsWith(".xlsx") && !filename.endsWith(".csv")) {
            return new EtlResultSummary(0, 0, List.of("Only .xlsx and .csv files are supported."), List.of());
        }

        EtlResultSummary parsed = filename.endsWith(".csv")
                ? csvParserService.parse(file)
                : excelParserService.parse(file);

        try {
            if (!parsed.getErrors().isEmpty() || parsed.getTestCases().isEmpty()) {
                return parsed; // If parsing has errors, do not save
            }

            for (TestCase testcase : parsed.getTestCases()) {
                if (testCaseRepository.existsByWorkKey(testcase.getWorkKey())) {
                    parsed.getErrors().add("Skipped duplicate test case with workKey: " + testcase.getWorkKey());
                    continue;
                }
                testCaseRepository.save(testcase);
            }
            return parsed;

        } catch (Exception e) {
            return new EtlResultSummary(0, 0, List.of("Unexpected error: " + e.getMessage()), List.of());
        }
    }
}
