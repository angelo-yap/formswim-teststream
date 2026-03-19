package com.formswim.teststream.etl.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "upload_review_item")
public class UploadReviewItem {

    public static final String TYPE_NEW = "NEW";
    public static final String TYPE_CHANGED_DUPLICATE = "CHANGED_DUPLICATE";

    public static final String ACTION_PENDING = "PENDING";
    public static final String ACTION_SKIP = "SKIP";
    public static final String ACTION_MERGE = "MERGE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private UploadReviewSession session;

    @Column(name = "work_key", nullable = false, length = 100)
    private String workKey;

    @Column(name = "conflict_type", nullable = false, length = 30)
    private String conflictType;

    @Column(name = "chosen_action", nullable = false, length = 20)
    private String chosenAction = ACTION_PENDING;

    @Column(name = "existing_snapshot_json", columnDefinition = "TEXT")
    private String existingSnapshotJson;

    @Column(name = "incoming_snapshot_json", nullable = false, columnDefinition = "TEXT")
    private String incomingSnapshotJson;

    @Column(name = "edited_snapshot_json", columnDefinition = "TEXT")
    private String editedSnapshotJson;

    @Column(name = "changed_fields_json", columnDefinition = "TEXT")
    private String changedFieldsJson;

    protected UploadReviewItem() {
    }

    public UploadReviewItem(String workKey,
                            String conflictType,
                            String existingSnapshotJson,
                            String incomingSnapshotJson,
                            String changedFieldsJson) {
        this.workKey = workKey;
        this.conflictType = conflictType;
        this.existingSnapshotJson = existingSnapshotJson;
        this.incomingSnapshotJson = incomingSnapshotJson;
        this.changedFieldsJson = changedFieldsJson;
    }

    void setSession(UploadReviewSession session) {
        this.session = session;
    }

    public Long getId() {
        return id;
    }

    public UploadReviewSession getSession() {
        return session;
    }

    public String getWorkKey() {
        return workKey;
    }

    public String getConflictType() {
        return conflictType;
    }

    public String getChosenAction() {
        return chosenAction;
    }

    public void setChosenAction(String chosenAction) {
        this.chosenAction = chosenAction;
    }

    public String getExistingSnapshotJson() {
        return existingSnapshotJson;
    }

    public String getIncomingSnapshotJson() {
        return incomingSnapshotJson;
    }

    public String getEditedSnapshotJson() {
        return editedSnapshotJson;
    }

    public void setEditedSnapshotJson(String editedSnapshotJson) {
        this.editedSnapshotJson = editedSnapshotJson;
    }

    public String getChangedFieldsJson() {
        return changedFieldsJson;
    }
}
