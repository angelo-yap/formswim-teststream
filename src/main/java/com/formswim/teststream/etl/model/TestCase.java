package com.formswim.teststream.etl.model;

import java.util.ArrayList;
import java.util.List;

// Represents one QMetry test case, mapping to a test case block in the export.
public class TestCase {

    private String workKey;
    private String summary;
    private String description;
    private String precondition;
    private String status;
    private String priority;
    private String assignee;
    private String reporter;
    private String estimatedTime;
    private String labels;
    private String components;
    private String sprint;
    private String fixVersions;
    private String version;
    private String folder;
    private String testCaseType;
    private String createdBy;
    private String createdOn;
    private String updatedBy;
    private String updatedOn;
    private String storyLinkages;
    private String isSharableStep;
    private String flakyScore;
    private List<TestStep> steps = new ArrayList<>();

    public TestCase(String workKey, String summary, String description,
                    String precondition, String status, String priority,
                    String assignee, String reporter, String estimatedTime,
                    String labels, String components, String sprint,
                    String fixVersions, String version, String folder,
                    String testCaseType, String createdBy, String createdOn,
                    String updatedBy, String updatedOn, String storyLinkages,
                    String isSharableStep, String flakyScore) {
        this.workKey = workKey;
        this.summary = summary;
        this.description = description;
        this.precondition = precondition;
        this.status = status;
        this.priority = priority;
        this.assignee = assignee;
        this.reporter = reporter;
        this.estimatedTime = estimatedTime;
        this.labels = labels;
        this.components = components;
        this.sprint = sprint;
        this.fixVersions = fixVersions;
        this.version = version;
        this.folder = folder;
        this.testCaseType = testCaseType;
        this.createdBy = createdBy;
        this.createdOn = createdOn;
        this.updatedBy = updatedBy;
        this.updatedOn = updatedOn;
        this.storyLinkages = storyLinkages;
        this.isSharableStep = isSharableStep;
        this.flakyScore = flakyScore;
    }

    public void addStep(TestStep step) {
        steps.add(step);
    }

    public String getWorkKey() {
        return workKey;
    }

    public String getSummary() {
        return summary;
    }

    public String getDescription() {
        return description;
    }

    public String getPrecondition() {
        return precondition;
    }

    public String getStatus() {
        return status;
    }

    public String getPriority() {
        return priority;
    }

    public String getAssignee() {
        return assignee;
    }

    public String getReporter() {
        return reporter;
    }

    public String getEstimatedTime() {
        return estimatedTime;
    }

    public String getLabels() {
        return labels;
    }

    public String getComponents() {
        return components;
    }

    public String getSprint() {
        return sprint;
    }

    public String getFixVersions() {
        return fixVersions;
    }

    public String getVersion() {
        return version;
    }

    public String getFolder() {
        return folder;
    }

    public String getTestCaseType() {
        return testCaseType;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public String getCreatedOn() {
        return createdOn;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public String getUpdatedOn() {
        return updatedOn;
    }

    public String getStoryLinkages() {
        return storyLinkages;
    }

    public String getIsSharableStep() {
        return isSharableStep;
    }

    public String getFlakyScore() {
        return flakyScore;
    }

    public List<TestStep> getSteps() {
        return steps;
    }
}

