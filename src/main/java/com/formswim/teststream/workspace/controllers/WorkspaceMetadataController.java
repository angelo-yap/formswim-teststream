package com.formswim.teststream.workspace.controllers;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.formswim.teststream.auth.model.AppUser;
import com.formswim.teststream.auth.service.CurrentUserService;
import com.formswim.teststream.shared.domain.TestCaseRepository;
import com.formswim.teststream.workspace.services.WorkspaceQueryService;

import jakarta.servlet.http.HttpSession;

// Owns all /api for components, folders, statuses, and other metadata

@Controller
@RequestMapping
public class WorkspaceMetadataController {
    private final TestCaseRepository testCaseRepository;
    private final CurrentUserService currentUserService;
    private final WorkspaceQueryService workspaceQueryService;


    public WorkspaceMetadataController(TestCaseRepository testCaseRepository,
                                       CurrentUserService currentUserService,
                                        WorkspaceQueryService workspaceQueryService
    ) {
        this.testCaseRepository = testCaseRepository;
        this.currentUserService = currentUserService;
        this.workspaceQueryService = workspaceQueryService;
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
        List<String> folders = testCaseRepository.findDistinctFolderByTeamKey(user.getTeamKey())
            .stream()
            .sorted()
            .collect(Collectors.toList());
        return ResponseEntity.ok(folders);
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
