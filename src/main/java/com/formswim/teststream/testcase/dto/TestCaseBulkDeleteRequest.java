package com.formswim.teststream.testcase.dto;

import java.util.List;

public class TestCaseBulkDeleteRequest {

    private List<String> workKeys;

    public List<String> getWorkKeys() {
        return workKeys;
    }

    public void setWorkKeys(List<String> workKeys) {
        this.workKeys = workKeys;
    }
}
