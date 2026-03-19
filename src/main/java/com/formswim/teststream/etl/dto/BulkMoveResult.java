package com.formswim.teststream.etl.dto;

import java.util.ArrayList;
import java.util.List;

public class BulkMoveResult {

    private int requestedCount;
    private int movedCount;
    private int invalidCount;
    private int forbiddenCount;
    private int notFoundCount;
    private List<BulkMoveFailure> failures = new ArrayList<>();

    public int getRequestedCount() {
        return requestedCount;
    }

    public void setRequestedCount(int requestedCount) {
        this.requestedCount = requestedCount;
    }

    public int getMovedCount() {
        return movedCount;
    }

    public void setMovedCount(int movedCount) {
        this.movedCount = movedCount;
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

    public List<BulkMoveFailure> getFailures() {
        if (failures == null) {
            failures = new ArrayList<>();
        }
        return failures;
    }

    public void setFailures(List<BulkMoveFailure> failures) {
        this.failures = failures == null ? new ArrayList<>() : new ArrayList<>(failures);
    }

    public static BulkMoveResult phaseOneNotImplemented(int requestedCount) {
        BulkMoveResult result = new BulkMoveResult();
        result.setRequestedCount(requestedCount);
        return result;
    }

    public static class BulkMoveFailure {

        private String workKey;
        private String reason;

        public BulkMoveFailure() {
        }

        public BulkMoveFailure(String workKey, String reason) {
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
