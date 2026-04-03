package com.formswim.teststream.workspace.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.formswim.teststream.shared.domain.TestCaseRepository;
import com.formswim.teststream.workspace.model.Folder;
import com.formswim.teststream.workspace.repository.FolderRepository;

@Service
public class FolderBackfillService {

    private static final String BACKFILL_ACTOR = "system-backfill";

    private final TestCaseRepository testCaseRepository;
    private final FolderRepository folderRepository;

    public FolderBackfillService(TestCaseRepository testCaseRepository,
                                 FolderRepository folderRepository) {
        this.testCaseRepository = testCaseRepository;
        this.folderRepository = folderRepository;
    }

    @Transactional
    public int backfillFoldersFromTestCases() {
        if (folderRepository.count() > 0) {
            return 0;
        }

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
                    byPath.put(path, folder);
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
                    String lookupPath = currentPath;

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
                segments.add(segment);
            }
        }

        return segments;
    }
}