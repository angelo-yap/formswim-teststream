package com.formswim.teststream.ingestion.dto;

public class ReviewStepSnapshot {

    private int stepNumber;
    private String stepSummary;
    private String testData;
    private String expectedResult;

    public ReviewStepSnapshot() {
    }

    public ReviewStepSnapshot(int stepNumber, String stepSummary, String testData, String expectedResult) {
        this.stepNumber = stepNumber;
        this.stepSummary = stepSummary;
        this.testData = testData;
        this.expectedResult = expectedResult;
    }

    public int getStepNumber() {
        return stepNumber;
    }

    public void setStepNumber(int stepNumber) {
        this.stepNumber = stepNumber;
    }

    public String getStepSummary() {
        return stepSummary;
    }

    public void setStepSummary(String stepSummary) {
        this.stepSummary = stepSummary;
    }

    public String getTestData() {
        return testData;
    }

    public void setTestData(String testData) {
        this.testData = testData;
    }

    public String getExpectedResult() {
        return expectedResult;
    }

    public void setExpectedResult(String expectedResult) {
        this.expectedResult = expectedResult;
    }
}
