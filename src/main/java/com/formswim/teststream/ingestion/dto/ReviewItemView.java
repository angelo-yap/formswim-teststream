package com.formswim.teststream.ingestion.dto;

import java.util.ArrayList;
import java.util.List;

public class ReviewItemView {

    private Long itemId;
    private String workKey;
    private String chosenAction;
    private List<FieldDifference> changedFields = new ArrayList<>();
    private ReviewCaseSnapshot existingSnapshot;
    private ReviewCaseSnapshot incomingSnapshot;
    private ReviewCaseSnapshot editableSnapshot;

    public ReviewItemView() {
    }

    public ReviewItemView(Long itemId,
                          String workKey,
                          String chosenAction,
                          List<FieldDifference> changedFields,
                          ReviewCaseSnapshot existingSnapshot,
                          ReviewCaseSnapshot incomingSnapshot,
                          ReviewCaseSnapshot editableSnapshot) {
        this.itemId = itemId;
        this.workKey = workKey;
        this.chosenAction = chosenAction;
        this.changedFields = changedFields == null ? new ArrayList<>() : new ArrayList<>(changedFields);
        this.existingSnapshot = existingSnapshot;
        this.incomingSnapshot = incomingSnapshot;
        this.editableSnapshot = editableSnapshot;
    }

    public Long getItemId() {
        return itemId;
    }

    public String getWorkKey() {
        return workKey;
    }

    public String getChosenAction() {
        return chosenAction;
    }

    public List<FieldDifference> getChangedFields() {
        return changedFields;
    }

    public ReviewCaseSnapshot getExistingSnapshot() {
        return existingSnapshot;
    }

    public ReviewCaseSnapshot getIncomingSnapshot() {
        return incomingSnapshot;
    }

    public ReviewCaseSnapshot getEditableSnapshot() {
        return editableSnapshot;
    }
}
