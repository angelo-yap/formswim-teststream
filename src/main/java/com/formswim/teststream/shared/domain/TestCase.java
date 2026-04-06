package com.formswim.teststream.shared.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;

// Represents one QMetry test case, mapping to a test case block in the export.
@Entity
@Table(
    name = "test_case",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_test_case_team_work_key", columnNames = { "team_key", "work_key" })
    }
)
public class TestCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_key", length = 100)
    private String teamKey;

    @Column(name = "work_key", nullable = false, length = 100)
    private String workKey;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String precondition;

    @Column(length = 100)
    private String status;

    @Column(length = 100)
    private String priority;

    @Column(name = "assignee", length = 255)
    private String assignee;

    @Column(name = "reporter", length = 255)
    private String reporter;

    @Column(name = "estimated_time", length = 100)
    private String estimatedTime;

    @Column(columnDefinition = "TEXT")
    private String labels;

    @Column(name = "components", length = 255)
    private String components;

    @Column(name = "sprint", length = 255)
    private String sprint;

    @Column(name = "fix_versions", length = 255)
    private String fixVersions;

    @Column(name = "version", length = 100)
    private String version;

    @Column(columnDefinition = "TEXT")
    private String folder;

    @Column(name = "test_case_type", length = 100)
    private String testCaseType;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "created_on", length = 100)
    private String createdOn;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    @Column(name = "updated_on", length = 100)
    private String updatedOn;

    @Column(columnDefinition = "TEXT")
    private String storyLinkages;

    @Column(name = "is_sharable_step", length = 10)
    private String isSharableStep;

    @Column(name = "flaky_score", length = 50)
    private String flakyScore;

    @OneToMany(mappedBy = "testCase", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepNumber ASC")
    private List<TestStep> steps = new ArrayList<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @JoinTable(
        name = "test_case_custom_tags",
        joinColumns = @JoinColumn(name = "test_case_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private List<Tag> customTags = new ArrayList<>();

    protected TestCase() {
    }

    public TestCase(String teamKey, String workKey, String summary, String description,
                    String precondition, String status, String priority,
                    String assignee, String reporter, String estimatedTime,
                    String labels, String components, String sprint,
                    String fixVersions, String version, String folder,
                    String testCaseType, String createdBy, String createdOn,
                    String updatedBy, String updatedOn, String storyLinkages,
                    String isSharableStep, String flakyScore) {
        this.teamKey = teamKey;
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
        step.setTestCase(this);
        steps.add(step);
    }

    public void replaceSteps(List<TestStep> replacement) {
        this.steps.clear();
        if (replacement == null) {
            return;
        }
        for (TestStep step : replacement) {
            addStep(step);
        }
    }

    public Long getId() {
        return id;
    }

    public String getTeamKey() {
        return teamKey;
    }

    public void setTeamKey(String teamKey) {
        this.teamKey = teamKey;
    }

    public String getWorkKey() {
        return workKey;
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

    public List<TestStep> getSteps() {
        return steps;
    }

    public List<Tag> getCustomTags() {
        return customTags;
    }

    public void setCustomTags(List<Tag> customTags) {
        this.customTags = customTags;
    }
}
