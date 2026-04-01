package com.formswim.teststream.ingestion.dto;

import java.util.ArrayList;
import java.util.List;

public class ReviewCaseSnapshot {

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
    private List<ReviewStepSnapshot> steps = new ArrayList<>();

    public ReviewCaseSnapshot() {
    }

    public ReviewCaseSnapshot copy() {
        ReviewCaseSnapshot copy = new ReviewCaseSnapshot();
        copy.workKey = workKey;
        copy.summary = summary;
        copy.description = description;
        copy.precondition = precondition;
        copy.status = status;
        copy.priority = priority;
        copy.assignee = assignee;
        copy.reporter = reporter;
        copy.estimatedTime = estimatedTime;
        copy.labels = labels;
        copy.components = components;
        copy.sprint = sprint;
        copy.fixVersions = fixVersions;
        copy.version = version;
        copy.folder = folder;
        copy.testCaseType = testCaseType;
        copy.createdBy = createdBy;
        copy.createdOn = createdOn;
        copy.updatedBy = updatedBy;
        copy.updatedOn = updatedOn;
        copy.storyLinkages = storyLinkages;
        copy.isSharableStep = isSharableStep;
        copy.flakyScore = flakyScore;
        copy.steps = new ArrayList<>();
        for (ReviewStepSnapshot step : steps) {
            copy.steps.add(new ReviewStepSnapshot(step.getStepNumber(), step.getStepSummary(), step.getTestData(), step.getExpectedResult()));
        }
        return copy;
    }

    public String getWorkKey() {
        return workKey;
    }

    public void setWorkKey(String workKey) {
        this.workKey = workKey;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPrecondition() {
        return precondition;
    }

    public void setPrecondition(String precondition) {
        this.precondition = precondition;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getAssignee() {
        return assignee;
    }

    public void setAssignee(String assignee) {
        this.assignee = assignee;
    }

    public String getReporter() {
        return reporter;
    }

    public void setReporter(String reporter) {
        this.reporter = reporter;
    }

    public String getEstimatedTime() {
        return estimatedTime;
    }

    public void setEstimatedTime(String estimatedTime) {
        this.estimatedTime = estimatedTime;
    }

    public String getLabels() {
        return labels;
    }

    public void setLabels(String labels) {
        this.labels = labels;
    }

    public String getComponents() {
        return components;
    }

    public void setComponents(String components) {
        this.components = components;
    }

    public String getSprint() {
        return sprint;
    }

    public void setSprint(String sprint) {
        this.sprint = sprint;
    }

    public String getFixVersions() {
        return fixVersions;
    }

    public void setFixVersions(String fixVersions) {
        this.fixVersions = fixVersions;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getFolder() {
        return folder;
    }

    public void setFolder(String folder) {
        this.folder = folder;
    }

    public String getTestCaseType() {
        return testCaseType;
    }

    public void setTestCaseType(String testCaseType) {
        this.testCaseType = testCaseType;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(String createdOn) {
        this.createdOn = createdOn;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public String getUpdatedOn() {
        return updatedOn;
    }

    public void setUpdatedOn(String updatedOn) {
        this.updatedOn = updatedOn;
    }

    public String getStoryLinkages() {
        return storyLinkages;
    }

    public void setStoryLinkages(String storyLinkages) {
        this.storyLinkages = storyLinkages;
    }

    public String getIsSharableStep() {
        return isSharableStep;
    }

    public void setIsSharableStep(String isSharableStep) {
        this.isSharableStep = isSharableStep;
    }

    public String getFlakyScore() {
        return flakyScore;
    }

    public void setFlakyScore(String flakyScore) {
        this.flakyScore = flakyScore;
    }

    public List<ReviewStepSnapshot> getSteps() {
        if (steps == null) {
            steps = new ArrayList<>();
        }
        return steps;
    }

    public void setSteps(List<ReviewStepSnapshot> steps) {
        this.steps = steps == null ? new ArrayList<>() : new ArrayList<>(steps);
    }
}
