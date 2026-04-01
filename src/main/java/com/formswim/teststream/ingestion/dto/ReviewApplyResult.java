package com.formswim.teststream.ingestion.dto;

public class ReviewApplyResult {

    private int importedNewCount;
    private int mergedCount;
    private int skippedCount;

    public ReviewApplyResult(int importedNewCount, int mergedCount, int skippedCount) {
        this.importedNewCount = importedNewCount;
        this.mergedCount = mergedCount;
        this.skippedCount = skippedCount;
    }

    public int getImportedNewCount() {
        return importedNewCount;
    }

    public int getMergedCount() {
        return mergedCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }
}
