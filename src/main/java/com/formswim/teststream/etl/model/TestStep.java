package com.formswim.teststream.etl.model;

import jakarta.persistence.*;

// Represents one step within a TestCase.
@Entity
@Table(name = "test_step")
public class TestStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "step_number", nullable = false)
    private int stepNumber;

    @Column(name = "step_summary", columnDefinition = "TEXT")
    private String stepSummary;

    @Column(name = "test_data", columnDefinition = "TEXT")
    private String testData;

    @Column(name = "expected_result", columnDefinition = "TEXT")
    private String expectedResult;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_case_id", nullable = false)
    private TestCase testCase;

    protected TestStep() {
    }

    public TestStep(int stepNumber, String stepSummary, String testData, String expectedResult) {
        this.stepNumber = stepNumber;
        this.stepSummary = stepSummary;
        this.testData = testData;
        this.expectedResult = expectedResult;
    }

    public void setTestCase(TestCase testCase) {
        this.testCase = testCase;
    }

    public Long getId() {
        return id;
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
