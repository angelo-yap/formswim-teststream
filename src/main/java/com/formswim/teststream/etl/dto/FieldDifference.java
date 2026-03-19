package com.formswim.teststream.etl.dto;

public class FieldDifference {

    private String fieldKey;
    private String displayName;
    private String existingValue;
    private String incomingValue;

    public FieldDifference() {
    }

    public FieldDifference(String fieldKey, String displayName, String existingValue, String incomingValue) {
        this.fieldKey = fieldKey;
        this.displayName = displayName;
        this.existingValue = existingValue;
        this.incomingValue = incomingValue;
    }

    public String getFieldKey() {
        return fieldKey;
    }

    public void setFieldKey(String fieldKey) {
        this.fieldKey = fieldKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getExistingValue() {
        return existingValue;
    }

    public void setExistingValue(String existingValue) {
        this.existingValue = existingValue;
    }

    public String getIncomingValue() {
        return incomingValue;
    }

    public void setIncomingValue(String incomingValue) {
        this.incomingValue = incomingValue;
    }
}
