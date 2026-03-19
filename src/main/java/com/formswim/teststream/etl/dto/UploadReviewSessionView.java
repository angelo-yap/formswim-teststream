package com.formswim.teststream.etl.dto;

import java.util.ArrayList;
import java.util.List;

public class UploadReviewSessionView {

    private String sessionId;
    private String originalFilename;
    private int parsedTestCaseCount;
    private int parsedStepCount;
    private int newItemCount;
    private int changedItemCount;
    private int duplicateUnchangedCount;
    private List<ReviewItemView> conflictItems = new ArrayList<>();

    public UploadReviewSessionView() {
    }

    public UploadReviewSessionView(String sessionId,
                                   String originalFilename,
                                   int parsedTestCaseCount,
                                   int parsedStepCount,
                                   int newItemCount,
                                   int changedItemCount,
                                   int duplicateUnchangedCount,
                                   List<ReviewItemView> conflictItems) {
        this.sessionId = sessionId;
        this.originalFilename = originalFilename;
        this.parsedTestCaseCount = parsedTestCaseCount;
        this.parsedStepCount = parsedStepCount;
        this.newItemCount = newItemCount;
        this.changedItemCount = changedItemCount;
        this.duplicateUnchangedCount = duplicateUnchangedCount;
        this.conflictItems = conflictItems == null ? new ArrayList<>() : new ArrayList<>(conflictItems);
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public int getParsedTestCaseCount() {
        return parsedTestCaseCount;
    }

    public int getParsedStepCount() {
        return parsedStepCount;
    }

    public int getNewItemCount() {
        return newItemCount;
    }

    public int getChangedItemCount() {
        return changedItemCount;
    }

    public int getDuplicateUnchangedCount() {
        return duplicateUnchangedCount;
    }

    public List<ReviewItemView> getConflictItems() {
        return conflictItems;
    }
}
