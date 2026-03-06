package com.formswim.teststream.etl.dto;

import com.formswim.teststream.etl.model.TestCase;
import java.util.List;

// Returned to the client after a parse attempt.
public class EtlResultSummary {

    private int testCasesParsed;
    private int totalStepsParsed;
    private List<String> errors;
    private List<TestCase> testCases;

    public EtlResultSummary(int testCasesParsed, int totalStepsParsed, List<String> errors, List<TestCase> testCases) {
        this.testCasesParsed = testCasesParsed;
        this.totalStepsParsed = totalStepsParsed;
        this.errors = errors;
        this.testCases = testCases;
    }

    public int getTestCasesParsed() { 
        return testCasesParsed; 
    }

    public int getTotalStepsParsed() {
        return totalStepsParsed; 
    }

    public List<String> getErrors() { 
        return errors; 
    }

    public List<TestCase> getTestCases() { 
        return testCases; 
    }
}
