package com.formswim.teststream.etl.service;
// Transactional creater (Load & Hash Check)

// Potentiall should be moved ot test?

import com.formswim.teststream.etl.dto.EtlResultSummary;
import com.formswim.teststream.etl.model.TestCase;
import com.formswim.teststream.etl.repository.TestCaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@Service
public class TestIngestionService {

    private final ExcelParserService excelParserService;
    private final TestCaseRepository testCaseRepository;

    public TestIngestionService(ExcelParserService excelParserService,
            TestCaseRepository testCaseRepository) {
        this.excelParserService = excelParserService;
        this.testCaseRepository = testCaseRepository;
    }

    @Transactional
    public EtlResultSummary ingestFile(MultipartFile file) {
        if (file.isEmpty() || !file.getOriginalFilename().endsWith(".xlsx")) {
            return new EtlResultSummary(0, 0, List.of("Invalid file. only .xlsx files are supported."), List.of());
        }
        EtlResultSummary parsed = excelParserService.parse(file);

        try {
            if (parsed.getErrors().size() > 0 || parsed.getTestCases().isEmpty()) {
                return parsed; // If parsing has error do not save
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
