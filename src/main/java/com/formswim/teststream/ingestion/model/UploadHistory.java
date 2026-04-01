package com.formswim.teststream.ingestion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(
    name = "upload_history",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_upload_history_team_hash", columnNames = { "team_key", "file_hash" })
    }
)
public class UploadHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_key", nullable = false, length = 100)
    private String teamKey;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "file_hash", nullable = false, length = 64)
    private String fileHash;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    protected UploadHistory() {
    }

    public UploadHistory(String teamKey, String originalFilename, String fileHash) {
        this.teamKey = teamKey;
        this.originalFilename = originalFilename;
        this.fileHash = fileHash;
    }

    @PrePersist
    void prePersist() {
        if (uploadedAt == null) {
            uploadedAt = Instant.now();
        }
    }

    public Long getId() {
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

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
