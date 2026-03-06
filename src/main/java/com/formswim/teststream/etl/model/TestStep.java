package com.formswim.teststream.etl.model;

// Represents one step within a TestCase.
public class TestStep {

    private int stepNumber;
    private String stepSummary;
    private String testData;
    private String expectedResult;

    public TestStep(int stepNumber, String stepSummary, String testData, String expectedResult) {
        this.stepNumber = stepNumber;
        this.stepSummary = stepSummary;
        this.testData = testData;
        this.expectedResult = expectedResult;
    }

    public int getStepNumber() {
        return stepNumber;
    }

    public String getStepSummary() {
        return stepSummary;
    }

    public String getTestData() {
        return testData;
    }

    public String getExpectedResult() {
        return expectedResult;
    }
}

