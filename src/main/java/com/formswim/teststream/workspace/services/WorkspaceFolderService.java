package com.formswim.teststream.workspace.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.formswim.teststream.workspace.model.Folder;
import com.formswim.teststream.workspace.repository.FolderRepository;

@Service
public class WorkspaceFolderService {

    private final FolderRepository folderRepository;

    public WorkspaceFolderService(FolderRepository folderRepository) {
        this.folderRepository = folderRepository;
    }

    @Transactional(readOnly = true)
    public List<String> listFolderPathsByTeamKey(String teamKey) {
        if (teamKey == null || teamKey.isBlank()) {
            return List.of();
        }

        List<Folder> folders = folderRepository.findByTeamKeyOrderByIdAsc(teamKey);
        if (folders.isEmpty()) {
            return List.of();
        }

        Map<Long, Folder> byId = new HashMap<>();
        for (Folder folder : folders) {
            byId.put(folder.getId(), folder);
        }

        Map<Long, String> pathMemo = new HashMap<>();
        List<String> paths = new ArrayList<>(folders.size());
        for (Folder folder : folders) {
            String path = derivePath(folder, pathMemo, byId);
            if (!path.isBlank()) {
                paths.add(path);
            }
        }

        paths.sort(String::compareTo);
        return Collections.unmodifiableList(paths);
    }

    private String derivePath(Folder folder, Map<Long, String> pathMemo, Map<Long, Folder> byId) {
        if (folder == null || folder.getId() == null) {
            return "";
        }

        String cached = pathMemo.get(folder.getId());
        if (cached != null) {
            return cached;
        }

        String name = folder.getName() == null ? "" : folder.getName().trim();
        if (name.isBlank()) {
            pathMemo.put(folder.getId(), "");
            return "";
        }

        Folder parent = folder.getParent();
        String parentPath = "";
        if (parent != null && parent.getId() != null) {
            Folder resolvedParent = byId.getOrDefault(parent.getId(), parent);
            parentPath = derivePath(resolvedParent, pathMemo, byId);
        }

        String path = parentPath.isBlank() ? name : parentPath + "/" + name;
        pathMemo.put(folder.getId(), path);
        return path;
    }
}