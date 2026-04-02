package com.formswim.teststream.bulk.dto;

import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Request payload for PATCH /api/testcases/bulk-edit.
 *
 * <p>Callers provide a set of target test case work keys, optional literal text to find,
 * optional replacement text, optional field selectors, an optional case-sensitivity flag,
 * and an optional status assignment value.</p>
 */
public class BulkEditRequest {

    @NotNull(message = "workKeys is required")
    private List<String> workKeys = new ArrayList<>();

    private String findText;

    private String replaceText;

    private List<String> fields = new ArrayList<>();

    private Boolean caseSensitive;

    private String statusValue;

    /**
     * Returns requested work keys as a non-null list.
     */
    public List<String> getWorkKeys() {
        if (workKeys == null) {
            workKeys = new ArrayList<>();
        }
        return workKeys;
    }

    /**
     * Stores a defensive copy of incoming work keys.
     */
    public void setWorkKeys(List<String> workKeys) {
        this.workKeys = workKeys == null ? new ArrayList<>() : new ArrayList<>(workKeys);
    }

    /**
     * Literal token to search for during replacement.
     */
    public String getFindText() {
        return findText;
    }

    public void setFindText(String findText) {
        this.findText = findText;
    }

    /**
     * Literal text that replaces each find token match.
     */
    public String getReplaceText() {
        return replaceText;
    }

    public void setReplaceText(String replaceText) {
        this.replaceText = replaceText;
    }

    /**
     * Optional requested fields to edit; returns a non-null list.
     */
    public List<String> getFields() {
        if (fields == null) {
            fields = new ArrayList<>();
        }
        return fields;
    }

    /**
     * Stores a defensive copy of requested field selectors.
     */
    public void setFields(List<String> fields) {
        this.fields = fields == null ? new ArrayList<>() : new ArrayList<>(fields);
    }

    /**
     * Whether literal text replacement should match exact case.
     */
    public Boolean getCaseSensitive() {
        return caseSensitive;
    }

    public void setCaseSensitive(Boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

    /**
     * Optional status to apply directly to each selected test case.
     */
    public String getStatusValue() {
        return statusValue;
    }

    public void setStatusValue(String statusValue) {
        this.statusValue = statusValue;
    }
}
