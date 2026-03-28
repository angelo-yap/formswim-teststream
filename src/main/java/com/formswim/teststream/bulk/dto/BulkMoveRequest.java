package com.formswim.teststream.bulk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;

public class BulkMoveRequest {

    @NotNull(message = "workKeys is required")
    private List<String> workKeys = new ArrayList<>();

    @NotBlank(message = "targetFolder is required")
    private String targetFolder;

    public List<String> getWorkKeys() {
        if (workKeys == null) {
            workKeys = new ArrayList<>();
        }
        return workKeys;
    }

    public void setWorkKeys(List<String> workKeys) {
        this.workKeys = workKeys == null ? new ArrayList<>() : new ArrayList<>(workKeys);
    }

    public String getTargetFolder() {
        return targetFolder;
    }

    public void setTargetFolder(String targetFolder) {
        this.targetFolder = targetFolder;
    }
}
