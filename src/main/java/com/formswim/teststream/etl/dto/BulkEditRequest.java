package com.formswim.teststream.etl.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Request payload for PATCH /api/testcases/bulk-edit.
 *
 * <p>Callers provide a set of target test case work keys, literal text to find,
 * replacement text, and optional field selectors. If {@code fields} is omitted,
 * the service applies replacements to all supported editable text fields.</p>
 */
public class BulkEditRequest {

    @NotNull(message = "workKeys is required")
    private List<String> workKeys = new ArrayList<>();

    @NotBlank(message = "findText is required")
    private String findText;

    @NotNull(message = "replaceText is required")
    private String replaceText;

    private List<String> fields = new ArrayList<>();

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
}