package com.formswim.teststream.etl.service;
// This service will handle exporting TestCase and TestStep data back to Excel format for download or sharing.

import com.formswim.teststream.etl.model.TestCase;
import com.formswim.teststream.etl.repository.TestCaseRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ExcelExportService {

    private final TestCaseRepository testCaseRepository;

    public ExcelExportService(TestCaseRepository testCaseRepository) {
        this.testCaseRepository = testCaseRepository;
    }

    public List<TestCase> getAllTestCases() {
        return testCaseRepository.findAll();
    }
}
