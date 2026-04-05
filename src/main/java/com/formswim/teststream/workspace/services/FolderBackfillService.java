package com.formswim.teststream.workspace.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.formswim.teststream.shared.domain.TestCaseRepository;
import com.formswim.teststream.workspace.model.Folder;
import com.formswim.teststream.workspace.repository.FolderRepository;

@Service
public class FolderBackfillService {

    private static final Logger log = LoggerFactory.getLogger(FolderBackfillService.class);
    private static final String BACKFILL_ACTOR = "system-backfill";
    private static final int MAX_FOLDER_NAME_LENGTH = 255;

    private final TestCaseRepository testCaseRepository;
    private final FolderRepository folderRepository;

    public FolderBackfillService(TestCaseRepository testCaseRepository,
                                 FolderRepository folderRepository) {
        this.testCaseRepository = testCaseRepository;
        this.folderRepository = folderRepository;
    }

    @Transactional
    public int backfillFoldersFromTestCases() {
        int createdCount = 0;
        List<String> teamKeys = testCaseRepository.findDistinctTeamKeysWithFolders();

        for (String teamKey : teamKeys) {
            if (teamKey == null || teamKey.isBlank()) {
                continue;
            }

            List<Folder> existing = folderRepository.findByTeamKeyOrderByIdAsc(teamKey);
            Map<Long, Folder> byId = new HashMap<>();
            for (Folder folder : existing) {
                byId.put(folder.getId(), folder);
            }

            Map<String, Folder> byPath = new HashMap<>();
            Map<Long, String> pathMemo = new HashMap<>();
            for (Folder folder : existing) {
                String path = derivePath(folder, pathMemo, byId);
                if (!path.isBlank()) {
                    byPath.put(canonicalPathKey(path), folder);
                }
            }

            List<String> folderPaths = testCaseRepository.findDistinctFolderByTeamKey(teamKey);
            for (String rawPath : folderPaths) {
                List<String> segments = parseSegments(rawPath);
                if (segments.isEmpty()) {
                    continue;
                }

                Folder parent = null;
                String currentPath = "";
                for (String segment : segments) {
                    currentPath = currentPath.isEmpty() ? segment : currentPath + "/" + segment;
                    String lookupPath = canonicalPathKey(currentPath);

                    Folder node = byPath.get(lookupPath);
                    if (node == null) {
                        node = new Folder(segment, parent, teamKey, BACKFILL_ACTOR);
                        node = folderRepository.save(node);
                        byId.put(node.getId(), node);
                        byPath.put(lookupPath, node);
                        createdCount++;
                    }

                    parent = node;
                }
            }
        }

        return createdCount;
    }

    private String derivePath(Folder folder, Map<Long, String> pathMemo, Map<Long, Folder> byId) {
        if (folder == null || folder.getId() == null) {
            return "";
        }

        String memoized = pathMemo.get(folder.getId());
        if (memoized != null) {
            return memoized;
        }

        Folder parent = folder.getParent();
        String parentPath = "";
        if (parent != null && parent.getId() != null) {
            Folder resolvedParent = byId.getOrDefault(parent.getId(), parent);
            parentPath = derivePath(resolvedParent, pathMemo, byId);
        }

        String current = (folder.getName() == null ? "" : folder.getName().trim());
        String path = parentPath.isBlank() ? current : parentPath + "/" + current;
        pathMemo.put(folder.getId(), path);
        return path;
    }

    private String canonicalPathKey(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        return path.toLowerCase(Locale.ROOT);
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
            if (!segment.isBlank()) {
                if (segment.length() > MAX_FOLDER_NAME_LENGTH) {
                    log.warn("Skipping backfill path with oversized segment (>{} chars): {}", MAX_FOLDER_NAME_LENGTH, rawPath);
                    return List.of();
                }
                segments.add(segment);
            }
        }

        return segments;
    }
}