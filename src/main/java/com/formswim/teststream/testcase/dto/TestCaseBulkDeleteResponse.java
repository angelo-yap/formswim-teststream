package com.formswim.teststream.testcase.dto;

public record TestCaseBulkDeleteResponse(int requestedCount, int deletedCount, int missingCount) {
}
