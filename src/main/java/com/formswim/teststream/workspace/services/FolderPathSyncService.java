package com.formswim.teststream.workspace.services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.formswim.teststream.workspace.model.Folder;
import com.formswim.teststream.workspace.repository.FolderRepository;

@Service
public class FolderPathSyncService {

    private static final int MAX_FOLDER_NAME_LENGTH = 255;

    private final FolderRepository folderRepository;

    public FolderPathSyncService(FolderRepository folderRepository) {
        this.folderRepository = folderRepository;
    }

    @Transactional
    public void ensureFolderPathExists(String teamKey, String rawFolderPath, String actor) {
        if (teamKey == null || teamKey.isBlank()) {
            return;
        }

        List<String> segments = parseSegments(rawFolderPath);
        if (segments.isEmpty()) {
            return;
        }

        Folder parent = null;
        for (String segment : segments) {
            Folder existing;
            if (parent == null) {
                existing = folderRepository.findByTeamKeyAndParentIsNullAndNameIgnoreCase(teamKey, segment)
                    .orElse(null);
            } else {
                existing = folderRepository.findByTeamKeyAndParent_IdAndNameIgnoreCase(teamKey, parent.getId(), segment)
                    .orElse(null);
            }

            if (existing == null) {
                existing = folderRepository.save(new Folder(segment, parent, teamKey, normalizeActor(actor)));
            }

            parent = existing;
        }
    }

    @Transactional
    public void ensureFolderPathsExist(String teamKey, Collection<String> rawFolderPaths, String actor) {
        if (rawFolderPaths == null || rawFolderPaths.isEmpty()) {
            return;
        }

        for (String rawFolderPath : rawFolderPaths) {
            ensureFolderPathExists(teamKey, rawFolderPath, actor);
        }
    }

    private List<String> parseSegments(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return List.of();
        }

        String normalized = rawPath.trim()
            .replace('\\', '/')
            .replaceAll("/+", "/")
            .replaceAll("^/+", "")
            .replaceAll("/+$", "");

        if (normalized.isBlank()) {
            return List.of();
        }

        String[] rawSegments = normalized.split("/");
        List<String> segments = new ArrayList<>(rawSegments.length);
        for (String part : rawSegments) {
            String segment = part == null ? "" : part.trim();
            if (segment.isBlank()) {
                continue;
            }
            if (segment.length() > MAX_FOLDER_NAME_LENGTH) {
                throw new IllegalArgumentException("Folder segment exceeds max length of 255 characters.");
            }
            segments.add(segment);
        }

        return segments;
    }

    private String normalizeActor(String createdBy) {
        if (createdBy == null || createdBy.isBlank()) {
            return "system-sync";
        }
        return createdBy.trim();
    }
}