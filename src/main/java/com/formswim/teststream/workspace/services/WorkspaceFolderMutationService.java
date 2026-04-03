package com.formswim.teststream.workspace.services;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.formswim.teststream.shared.domain.TestCaseRepository;
import com.formswim.teststream.workspace.dto.FolderCreateRequest;
import com.formswim.teststream.workspace.dto.FolderResponse;
import com.formswim.teststream.workspace.dto.FolderUpdateRequest;
import com.formswim.teststream.workspace.model.Folder;
import com.formswim.teststream.workspace.repository.FolderRepository;

@Service
public class WorkspaceFolderMutationService {

    private static final int MAX_FOLDER_NAME_LENGTH = 255;

    private final FolderRepository folderRepository;
    private final TestCaseRepository testCaseRepository;

    public WorkspaceFolderMutationService(FolderRepository folderRepository,
                                          TestCaseRepository testCaseRepository) {
        this.folderRepository = folderRepository;
        this.testCaseRepository = testCaseRepository;
    }

    @Transactional(readOnly = true)
    public List<FolderResponse> listFolderNodesByTeamKey(String teamKey) {
        List<Folder> folders = folderRepository.findByTeamKeyOrderByIdAsc(teamKey);
        Map<Long, Folder> byId = indexById(folders);
        Map<Long, String> pathMemo = new HashMap<>();

        return folders.stream()
            .map(folder -> toResponse(folder, byId, pathMemo))
            .toList();
    }

    @Transactional
    public FolderResponse createFolder(String teamKey, String createdBy, FolderCreateRequest request) {
        if (request == null) {
            throw new FolderBadRequestException("Request body is required.");
        }

        String name = normalizeName(request.getName());
        Folder parent = resolveParent(teamKey, request.getParentId());
        ensureSiblingNameAvailable(teamKey, parent, name, null);

        Folder folder = new Folder(name, parent, teamKey, normalizeActor(createdBy));
        Folder saved = folderRepository.save(folder);

        Map<Long, Folder> byId = indexById(folderRepository.findByTeamKeyOrderByIdAsc(teamKey));
        return toResponse(saved, byId, new HashMap<>());
    }

    @Transactional
    public FolderResponse updateFolder(String teamKey, Long id, FolderUpdateRequest request) {
        if (request == null || (!request.isNameProvided() && !request.isParentIdProvided())) {
            throw new FolderBadRequestException("At least one field is required: name or parentId.");
        }

        Folder folder = folderRepository.findByTeamKeyAndId(teamKey, id)
            .orElseThrow(() -> new FolderNotFoundException("Folder not found."));

        Map<Long, Folder> byId = indexById(folderRepository.findByTeamKeyOrderByIdAsc(teamKey));

        String nextName = folder.getName();
        if (request.isNameProvided()) {
            nextName = normalizeName(request.getName());
        }

        Folder nextParent = folder.getParent();
        if (request.isParentIdProvided()) {
            nextParent = resolveParent(teamKey, request.getParentId());
            if (nextParent != null && isDescendantOf(nextParent, folder.getId(), byId)) {
                throw new FolderBadRequestException("Cannot move a folder into one of its descendants.");
            }
        }

        String previousPath = derivePath(folder, byId, new HashMap<>());
        String nextParentPath = nextParent == null ? "" : derivePath(nextParent, byId, new HashMap<>());
        String nextPath = nextParentPath.isBlank() ? nextName : nextParentPath + "/" + nextName;

        ensureSiblingNameAvailable(teamKey, nextParent, nextName, folder.getId());
        folder.setName(nextName);
        folder.setParent(nextParent);

        Folder saved = folderRepository.save(folder);

        if (!previousPath.equals(nextPath)) {
            migrateTestCaseFolderPaths(teamKey, previousPath, nextPath);
        }

        byId = indexById(folderRepository.findByTeamKeyOrderByIdAsc(teamKey));
        return toResponse(saved, byId, new HashMap<>());
    }

    @Transactional
    public void deleteFolder(String teamKey, Long id) {
        Folder folder = folderRepository.findByTeamKeyAndId(teamKey, id)
            .orElseThrow(() -> new FolderNotFoundException("Folder not found."));

        if (folderRepository.existsByTeamKeyAndParent_Id(teamKey, folder.getId())) {
            throw new FolderConflictException("Folder cannot be deleted because it contains subfolders.");
        }

        Map<Long, Folder> byId = indexById(folderRepository.findByTeamKeyOrderByIdAsc(teamKey));
        String folderPath = derivePath(folder, byId, new HashMap<>());
        long boundCases = testCaseRepository.countByTeamKeyAndFolderPathHierarchy(teamKey, folderPath);
        if (boundCases > 0) {
            throw new FolderConflictException("Folder cannot be deleted because it contains test cases.");
        }

        folderRepository.delete(folder);
    }

    private Folder resolveParent(String teamKey, Long parentId) {
        if (parentId == null) {
            return null;
        }

        return folderRepository.findByTeamKeyAndId(teamKey, parentId)
            .orElseThrow(() -> new FolderNotFoundException("Parent folder not found."));
    }

    private void ensureSiblingNameAvailable(String teamKey, Folder parent, String name, Long selfId) {
        Long parentId = parent == null ? null : parent.getId();
        if (parentId == null) {
            folderRepository.findByTeamKeyAndParentIsNullAndNameIgnoreCase(teamKey, name)
                .ifPresent(existing -> {
                    if (selfId == null || !existing.getId().equals(selfId)) {
                        throw new FolderConflictException("A folder with this name already exists in the target location.");
                    }
                });
            return;
        }

        folderRepository.findByTeamKeyAndParent_IdAndNameIgnoreCase(teamKey, parentId, name)
                .ifPresent(existing -> {
                    if (selfId == null || !existing.getId().equals(selfId)) {
                        throw new FolderConflictException("A folder with this name already exists in the target location.");
                    }
                });
    }

    private String normalizeName(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isBlank()) {
            throw new FolderBadRequestException("Folder name is required.");
        }

        if (name.length() > MAX_FOLDER_NAME_LENGTH) {
            throw new FolderBadRequestException("Folder name cannot exceed 255 characters.");
        }

        if (name.contains("/") || name.contains("\\")) {
            throw new FolderBadRequestException("Folder name cannot include path separators.");
        }

        return name;
    }

    private String normalizeActor(String createdBy) {
        if (createdBy == null || createdBy.isBlank()) {
            return "unknown";
        }
        return createdBy.trim();
    }

    private boolean isDescendantOf(Folder candidateParent, Long folderId, Map<Long, Folder> byId) {
        Folder current = candidateParent;
        while (current != null) {
            if (current.getId() != null && current.getId().equals(folderId)) {
                return true;
            }
            Folder parent = current.getParent();
            if (parent == null || parent.getId() == null) {
                return false;
            }
            current = byId.getOrDefault(parent.getId(), parent);
        }
        return false;
    }

    private Map<Long, Folder> indexById(List<Folder> folders) {
        Map<Long, Folder> byId = new HashMap<>();
        for (Folder folder : folders) {
            byId.put(folder.getId(), folder);
        }
        return byId;
    }

    private void migrateTestCaseFolderPaths(String teamKey, String previousPath, String nextPath) {
        if (previousPath == null || previousPath.isBlank() || nextPath == null || nextPath.isBlank()) {
            return;
        }

        testCaseRepository.bulkRepathExact(teamKey, previousPath, nextPath);
        testCaseRepository.bulkRepathDescendants(teamKey, previousPath, nextPath, previousPath.length() + 1);
    }

    private FolderResponse toResponse(Folder folder, Map<Long, Folder> byId, Map<Long, String> pathMemo) {
        Long parentId = folder.getParent() == null ? null : folder.getParent().getId();
        String path = derivePath(folder, byId, pathMemo);
        return new FolderResponse(folder.getId(), folder.getName(), parentId, path);
    }

    private String derivePath(Folder folder, Map<Long, Folder> byId, Map<Long, String> pathMemo) {
        if (folder == null || folder.getId() == null) {
            return "";
        }

        String cached = pathMemo.get(folder.getId());
        if (cached != null) {
            return cached;
        }

        String name = folder.getName() == null ? "" : folder.getName().trim();
        String path = name;

        Folder parent = folder.getParent();
        if (parent != null && parent.getId() != null) {
            Folder resolvedParent = byId.getOrDefault(parent.getId(), parent);
            String parentPath = derivePath(resolvedParent, byId, pathMemo);
            if (!parentPath.isBlank()) {
                path = parentPath + "/" + name;
            }
        }

        pathMemo.put(folder.getId(), path);
        return path;
    }
}