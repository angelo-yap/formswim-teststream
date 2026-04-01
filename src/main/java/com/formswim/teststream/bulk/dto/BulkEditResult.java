package com.formswim.teststream.bulk.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Response payload for PATCH /api/testcases/bulk-edit.
 *
 * <p>Contains summary counters for processed work keys, changed entities,
 * and classified non-fatal failures (invalid/forbidden/not found).</p>
 */
public class BulkEditResult {

    private int requestedCount;
    private int candidateCount;
    private int updatedCaseCount;
    private int updatedStepCount;
    private int totalReplacements;
    private int invalidCount;
    private int forbiddenCount;
    private int notFoundCount;
    private List<BulkEditFailure> failures = new ArrayList<>();

    /** Total number of work keys provided by caller. */
    public int getRequestedCount() {
        return requestedCount;
    }

    public void setRequestedCount(int requestedCount) {
        this.requestedCount = requestedCount;
    }

    /** Number of team-owned keys eligible for mutation. */
    public int getCandidateCount() {
        return candidateCount;
    }

    public void setCandidateCount(int candidateCount) {
        this.candidateCount = candidateCount;
    }

    /** Number of test case parents where at least one case field changed. */
    public int getUpdatedCaseCount() {
        return updatedCaseCount;
    }

    public void setUpdatedCaseCount(int updatedCaseCount) {
        this.updatedCaseCount = updatedCaseCount;
    }

    /** Number of child steps where at least one step field changed. */
    public int getUpdatedStepCount() {
        return updatedStepCount;
    }

    public void setUpdatedStepCount(int updatedStepCount) {
        this.updatedStepCount = updatedStepCount;
    }

    /** Total exact replacements applied across all edited fields. */
    public int getTotalReplacements() {
        return totalReplacements;
    }

    public void setTotalReplacements(int totalReplacements) {
        this.totalReplacements = totalReplacements;
    }

    public int getInvalidCount() {
        return invalidCount;
    }

    public void setInvalidCount(int invalidCount) {
        this.invalidCount = invalidCount;
    }

    public int getForbiddenCount() {
        return forbiddenCount;
    }

    public void setForbiddenCount(int forbiddenCount) {
        this.forbiddenCount = forbiddenCount;
    }

    public int getNotFoundCount() {
        return notFoundCount;
    }

    public void setNotFoundCount(int notFoundCount) {
        this.notFoundCount = notFoundCount;
    }

    /** Non-fatal per-key failures for caller diagnostics. */
    public List<BulkEditFailure> getFailures() {
        if (failures == null) {
            failures = new ArrayList<>();
        }
        return failures;
    }

    public void setFailures(List<BulkEditFailure> failures) {
        this.failures = failures == null ? new ArrayList<>() : new ArrayList<>(failures);
    }

    /**
     * Failure entry for one requested work key.
     */
    public static class BulkEditFailure {

        private String workKey;
        private String reason;

        public BulkEditFailure() {
        }

        public BulkEditFailure(String workKey, String reason) {
            this.workKey = workKey;
            this.reason = reason;
        }

        public String getWorkKey() {
            return workKey;
        }

        public void setWorkKey(String workKey) {
            this.workKey = workKey;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }
}