package com.formswim.teststream.ingestion.dto;

import java.util.ArrayList;
import java.util.List;

public class ReviewConflictCandidate {

    private String workKey;
    private ReviewCaseSnapshot existingSnapshot;
    private ReviewCaseSnapshot incomingSnapshot;
    private List<FieldDifference> fieldDifferences = new ArrayList<>();

    public ReviewConflictCandidate() {
    }

    public ReviewConflictCandidate(String workKey,
                                   ReviewCaseSnapshot existingSnapshot,
                                   ReviewCaseSnapshot incomingSnapshot,
                                   List<FieldDifference> fieldDifferences) {
        this.workKey = workKey;
        this.existingSnapshot = existingSnapshot;
        this.incomingSnapshot = incomingSnapshot;
        this.fieldDifferences = fieldDifferences == null ? new ArrayList<>() : new ArrayList<>(fieldDifferences);
    }

    public String getWorkKey() {
        return workKey;
    }

    public ReviewCaseSnapshot getExistingSnapshot() {
        return existingSnapshot;
    }

    public ReviewCaseSnapshot getIncomingSnapshot() {
        return incomingSnapshot;
    }

    public List<FieldDifference> getFieldDifferences() {
        return fieldDifferences;
    }
}
