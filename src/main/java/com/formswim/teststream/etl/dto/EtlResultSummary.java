package com.formswim.teststream.etl.dto;
import com.formswim.teststream.shared.domain.TestCase;

import java.util.ArrayList;
import java.util.List;

// Returned to the client after a parse/import attempt.
public class EtlResultSummary {

    private int testCasesParsed;
    private int totalStepsParsed;
    private int importedCount;
    private int duplicateUnchangedCount;
    private int duplicateChangedCount;
    private int stagedNewCount;
    private boolean exactDuplicateFile;
    private boolean reviewRequired;
    private String reviewSessionId;
    private String reviewUrl;
    private String message;
    private List<String> errors = new ArrayList<>();
    private List<TestCase> testCases = new ArrayList<>();

    public EtlResultSummary() {
    }

    public EtlResultSummary(int testCasesParsed, int totalStepsParsed, List<String> errors, List<TestCase> testCases) {
        this.testCasesParsed = testCasesParsed;
        this.totalStepsParsed = totalStepsParsed;
        this.errors = errors == null ? new ArrayList<>() : new ArrayList<>(errors);
        this.testCases = testCases == null ? new ArrayList<>() : new ArrayList<>(testCases);
    }

    public int getTestCasesParsed() {
        return testCasesParsed;
    }

    public void setTestCasesParsed(int testCasesParsed) {
        this.testCasesParsed = testCasesParsed;
    }

    public int getTotalStepsParsed() {
        return totalStepsParsed;
    }

    public void setTotalStepsParsed(int totalStepsParsed) {
        this.totalStepsParsed = totalStepsParsed;
    }

    public int getImportedCount() {
        return importedCount;
    }

    public void setImportedCount(int importedCount) {
        this.importedCount = importedCount;
    }

    public int getDuplicateUnchangedCount() {
        return duplicateUnchangedCount;
    }

    public void setDuplicateUnchangedCount(int duplicateUnchangedCount) {
        this.duplicateUnchangedCount = duplicateUnchangedCount;
    }

    public int getDuplicateChangedCount() {
        return duplicateChangedCount;
    }

    public void setDuplicateChangedCount(int duplicateChangedCount) {
        this.duplicateChangedCount = duplicateChangedCount;
    }

    public int getStagedNewCount() {
        return stagedNewCount;
    }

    public void setStagedNewCount(int stagedNewCount) {
        this.stagedNewCount = stagedNewCount;
    }

    public boolean isExactDuplicateFile() {
        return exactDuplicateFile;
    }

    public void setExactDuplicateFile(boolean exactDuplicateFile) {
        this.exactDuplicateFile = exactDuplicateFile;
    }

    public boolean isReviewRequired() {
        return reviewRequired;
    }

    public void setReviewRequired(boolean reviewRequired) {
        this.reviewRequired = reviewRequired;
    }

    public String getReviewSessionId() {
        return reviewSessionId;
    }

    public void setReviewSessionId(String reviewSessionId) {
        this.reviewSessionId = reviewSessionId;
    }

    public String getReviewUrl() {
        return reviewUrl;
    }

    public void setReviewUrl(String reviewUrl) {
        this.reviewUrl = reviewUrl;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<String> getErrors() {
        if (errors == null) {
            errors = new ArrayList<>();
        }
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors == null ? new ArrayList<>() : new ArrayList<>(errors);
    }

    public List<TestCase> getTestCases() {
        if (testCases == null) {
            testCases = new ArrayList<>();
        }
        return testCases;
    }

    public void setTestCases(List<TestCase> testCases) {
        this.testCases = testCases == null ? new ArrayList<>() : new ArrayList<>(testCases);
    }

    public void addError(String error) {
        getErrors().add(error);
    }
}
