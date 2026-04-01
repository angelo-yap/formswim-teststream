package com.formswim.teststream.ingestion.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "upload_review_session")
public class UploadReviewSession {

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_APPLIED = "APPLIED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "team_key", nullable = false, length = 100)
    private String teamKey;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "file_hash", nullable = false, length = 64)
    private String fileHash;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "parsed_test_case_count", nullable = false)
    private int parsedTestCaseCount;

    @Column(name = "parsed_step_count", nullable = false)
    private int parsedStepCount;

    @Column(name = "new_item_count", nullable = false)
    private int newItemCount;

    @Column(name = "changed_item_count", nullable = false)
    private int changedItemCount;

    @Column(name = "duplicate_unchanged_count", nullable = false)
    private int duplicateUnchangedCount;

    @OneToMany(mappedBy = "session", fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<UploadReviewItem> items = new ArrayList<>();

    protected UploadReviewSession() {
    }

    public UploadReviewSession(String teamKey,
                               String originalFilename,
                               String fileHash,
                               int parsedTestCaseCount,
                               int parsedStepCount,
                               int newItemCount,
                               int changedItemCount,
                               int duplicateUnchangedCount) {
        this.id = UUID.randomUUID().toString();
        this.teamKey = teamKey;
        this.originalFilename = originalFilename;
        this.fileHash = fileHash;
        this.status = STATUS_OPEN;
        this.parsedTestCaseCount = parsedTestCaseCount;
        this.parsedStepCount = parsedStepCount;
        this.newItemCount = newItemCount;
        this.changedItemCount = changedItemCount;
        this.duplicateUnchangedCount = duplicateUnchangedCount;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        if (status == null || status.isBlank()) {
            status = STATUS_OPEN;
        }
    }

    public void addItem(UploadReviewItem item) {
        item.setSession(this);
        items.add(item);
    }

    public void markApplied() {
        this.status = STATUS_APPLIED;
    }

    public void markCancelled() {
        this.status = STATUS_CANCELLED;
    }

    public String getId() {
        return id;
    }

    public String getTeamKey() {
        return teamKey;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getFileHash() {
        return fileHash;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
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

    public List<UploadReviewItem> getItems() {
        return items;
    }
}
