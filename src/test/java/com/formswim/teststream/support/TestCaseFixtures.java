package com.formswim.teststream.support;

import com.formswim.teststream.shared.domain.TestCase;

public final class TestCaseFixtures {

    private TestCaseFixtures() {
    }

    public static TestCase basicCase(String teamKey, String workKey, String folder) {
        return new TestCase(
            teamKey,
            workKey,
            "Summary " + workKey,
            "Description",
            "Precondition",
            "Draft",
            "Medium",
            "Assignee",
            "Reporter",
            "5m",
            "label",
            "component",
            "Sprint 1",
            "1.0",
            "V1",
            folder,
            "Regression",
            "creator@example.com",
            "2026-03-18",
            "editor@example.com",
            "2026-03-18",
            "ST-1",
            "No",
            "0"
        );
    }

    public static TestCase detailedCase(String teamKey,
                                        String workKey,
                                        String summary,
                                        String description,
                                        String precondition,
                                        String folder) {
        return new TestCase(
            teamKey,
            workKey,
            summary,
            description,
            precondition,
            "Draft",
            "High",
            "Alice",
            "Bob",
            "5m",
            "auth",
            "UI",
            "Sprint 1",
            "1.0",
            "V1",
            folder,
            "Regression",
            "creator@example.com",
            "2026-03-01",
            "editor@example.com",
            "2026-03-02",
            "ST-1",
            "No",
            "1"
        );
    }
}
