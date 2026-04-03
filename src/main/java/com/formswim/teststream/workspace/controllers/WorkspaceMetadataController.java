package com.formswim.teststream.workspace.controllers;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.formswim.teststream.auth.model.AppUser;
import com.formswim.teststream.auth.service.CurrentUserService;
import com.formswim.teststream.shared.domain.TestCaseRepository;
import com.formswim.teststream.workspace.dto.FolderCreateRequest;
import com.formswim.teststream.workspace.dto.FolderResponse;
import com.formswim.teststream.workspace.dto.FolderUpdateRequest;
import com.formswim.teststream.workspace.services.FolderBadRequestException;
import com.formswim.teststream.workspace.services.FolderConflictException;
import com.formswim.teststream.workspace.services.FolderNotFoundException;
import com.formswim.teststream.workspace.services.WorkspaceFolderService;
import com.formswim.teststream.workspace.services.WorkspaceFolderMutationService;
import com.formswim.teststream.workspace.services.WorkspaceQueryService;

import jakarta.servlet.http.HttpSession;

// Owns all /api for components, folders, statuses, and other metadata

@Controller
@RequestMapping
public class WorkspaceMetadataController {
    private final TestCaseRepository testCaseRepository;
    private final CurrentUserService currentUserService;
    private final WorkspaceQueryService workspaceQueryService;
    private final WorkspaceFolderService workspaceFolderService;
    private final WorkspaceFolderMutationService workspaceFolderMutationService;


    public WorkspaceMetadataController(TestCaseRepository testCaseRepository,
                                       CurrentUserService currentUserService,
                                        WorkspaceQueryService workspaceQueryService,
                                       WorkspaceFolderService workspaceFolderService,
                                       WorkspaceFolderMutationService workspaceFolderMutationService
    ) {
        this.testCaseRepository = testCaseRepository;
        this.currentUserService = currentUserService;
        this.workspaceQueryService = workspaceQueryService;
        this.workspaceFolderService = workspaceFolderService;
        this.workspaceFolderMutationService = workspaceFolderMutationService;
    }
    /**
     * GET /api/folders
     * Returns a JSON list of unique folder names 
     * for populating the workspace folder filter dropdown.
     * 
     * @return a JSON string array of unique folder names from the test cases in the database.
     */
    @GetMapping("/api/folders")
    @ResponseBody
    public ResponseEntity<List<String>> getFoldersByTeamKey(HttpSession session, Authentication authentication) {
        Optional<AppUser> currentUser = currentUserService.resolveCurrentUser(session, authentication);
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }
        AppUser user = currentUser.get();
        // Currently we have this null team key check, if solo teams in the future this would need to be resolved
        if (user.getTeamKey() == null || user.getTeamKey().isBlank()) {
            return ResponseEntity.status(403).build();
        }
        List<String> folders = workspaceFolderService.listFolderPathsByTeamKey(user.getTeamKey());
        return ResponseEntity.ok(folders);
    }

    @GetMapping("/api/folders/nodes")
    @ResponseBody
    public ResponseEntity<List<FolderResponse>> getFolderNodesByTeamKey(HttpSession session, Authentication authentication) {
        Optional<AppUser> currentUser = currentUserService.resolveCurrentUser(session, authentication);
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        AppUser user = currentUser.get();
        if (user.getTeamKey() == null || user.getTeamKey().isBlank()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<FolderResponse> folders = workspaceFolderMutationService.listFolderNodesByTeamKey(user.getTeamKey());
        return ResponseEntity.ok(folders);
    }

    @PostMapping("/api/folders")
    @ResponseBody
    public ResponseEntity<?> createFolder(@RequestBody FolderCreateRequest request,
                                          HttpSession session,
                                          Authentication authentication) {
        Optional<AppUser> currentUser = currentUserService.resolveCurrentUser(session, authentication);
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        AppUser user = currentUser.get();
        if (user.getTeamKey() == null || user.getTeamKey().isBlank()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            FolderResponse created = workspaceFolderMutationService.createFolder(user.getTeamKey(), user.getEmail(), request);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (FolderNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
        } catch (FolderBadRequestException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        } catch (FolderConflictException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
        }
    }

    @PatchMapping("/api/folders/{id}")
    @ResponseBody
    public ResponseEntity<?> updateFolder(@PathVariable Long id,
                                          @RequestBody FolderUpdateRequest request,
                                          HttpSession session,
                                          Authentication authentication) {
        Optional<AppUser> currentUser = currentUserService.resolveCurrentUser(session, authentication);
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        AppUser user = currentUser.get();
        if (user.getTeamKey() == null || user.getTeamKey().isBlank()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            FolderResponse updated = workspaceFolderMutationService.updateFolder(user.getTeamKey(), id, request);
            return ResponseEntity.ok(updated);
        } catch (FolderNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
        } catch (FolderBadRequestException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        } catch (FolderConflictException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
        }
    }

    @DeleteMapping("/api/folders/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteFolder(@PathVariable Long id,
                                          HttpSession session,
                                          Authentication authentication) {
        Optional<AppUser> currentUser = currentUserService.resolveCurrentUser(session, authentication);
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        AppUser user = currentUser.get();
        if (user.getTeamKey() == null || user.getTeamKey().isBlank()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            workspaceFolderMutationService.deleteFolder(user.getTeamKey(), id);
            return ResponseEntity.noContent().build();
        } catch (FolderNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
        } catch (FolderBadRequestException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        } catch (FolderConflictException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
        }
    }

    @GetMapping("/api/tags")
    @ResponseBody
    public ResponseEntity<List<String>> getTagsByTeamKey(HttpSession session, Authentication authentication) {
        Optional<AppUser> currentUser = currentUserService.resolveCurrentUser(session, authentication);
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }
        AppUser user = currentUser.get();
        if (user.getTeamKey() == null || user.getTeamKey().isBlank()) {
            return ResponseEntity.status(403).build();
        }

        Set<String> tags = new LinkedHashSet<>();
        testCaseRepository.findDistinctComponentsByTeamKey(user.getTeamKey()).forEach(value -> workspaceQueryService.addDelimitedTags(tags, value));
        testCaseRepository.findDistinctTestCaseTypeByTeamKey(user.getTeamKey()).forEach(value -> workspaceQueryService.addDelimitedTags(tags, value));

        List<String> sortedTags = tags.stream()
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .collect(Collectors.toList());
        return ResponseEntity.ok(sortedTags);
    }

    @GetMapping("/api/components")
    @ResponseBody
    public ResponseEntity<List<String>> getComponentsByTeamKey(HttpSession session, Authentication authentication) {
        Optional<AppUser> currentUser = currentUserService.resolveCurrentUser(session, authentication);
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }
        AppUser user = currentUser.get();
        if (user.getTeamKey() == null || user.getTeamKey().isBlank()) {
            return ResponseEntity.status(403).build();
        }

        Set<String> components = new LinkedHashSet<>();
        testCaseRepository.findDistinctComponentsByTeamKey(user.getTeamKey()).forEach(value -> workspaceQueryService.addDelimitedTags(components, value));

        List<String> sortedComponents = components.stream()
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .collect(Collectors.toList());
        return ResponseEntity.ok(sortedComponents);
    }

    @GetMapping("/api/statuses")
    @ResponseBody
    public ResponseEntity<List<String>> getStatusesByTeamKey(HttpSession session, Authentication authentication) {
        Optional<AppUser> currentUser = currentUserService.resolveCurrentUser(session, authentication);
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }
        AppUser user = currentUser.get();
        if (user.getTeamKey() == null || user.getTeamKey().isBlank()) {
            return ResponseEntity.status(403).build();
        }

        List<String> statuses = testCaseRepository.findDistinctStatusByTeamKey(user.getTeamKey())
            .stream()
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .collect(Collectors.toList());
        return ResponseEntity.ok(statuses);
    }
}
