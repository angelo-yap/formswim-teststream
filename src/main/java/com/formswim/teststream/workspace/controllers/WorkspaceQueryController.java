package com.formswim.teststream.workspace.controllers;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.formswim.teststream.auth.model.AppUser;
import com.formswim.teststream.auth.service.CurrentUserService;
import com.formswim.teststream.shared.domain.TestCase;
import com.formswim.teststream.shared.domain.TestCaseRepository;
import com.formswim.teststream.workspace.services.WorkspaceQueryService;

import jakarta.servlet.http.HttpSession;

// Owns /api/testcases

@Controller
@RequestMapping
public class WorkspaceQueryController {
    
    private final TestCaseRepository testCaseRepository;
    private final CurrentUserService currentUserService;
    private final WorkspaceQueryService workspaceQueryService;

    public WorkspaceQueryController(TestCaseRepository testCaseRepository,
                                    CurrentUserService currentUserService,
                                    WorkspaceQueryService workspaceQueryService
    ) {
        this.testCaseRepository = testCaseRepository;
        this.currentUserService = currentUserService;
        this.workspaceQueryService = workspaceQueryService;
    }
  
  
    @GetMapping("/api/testcases")
    @ResponseBody
    public ResponseEntity<List<TestCase>> apiGetTestCases(HttpSession session,
                                                          @RequestParam(required = false) String search,
                                                          @RequestParam(required = false) String status,
                                                          @RequestParam(required = false) String component,
                                                          @RequestParam(required = false) String tag,
                                                          @RequestParam(required = false) String folder,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "50") int size,
                                                          Authentication authentication) {
        Optional<AppUser> currentUser = currentUserService.resolveCurrentUser(session, authentication);
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }
        AppUser user = currentUser.get();
        if (user.getTeamKey() == null || user.getTeamKey().isBlank()) {
            return ResponseEntity.status(403).build();
        }

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);
        String normalizedSearch = workspaceQueryService.normalizeQueryParam(search);
        String normalizedStatus = workspaceQueryService.normalizeQueryParam(status);
        String normalizedComponent = workspaceQueryService.normalizeQueryParam(component);
        String normalizedTag = workspaceQueryService.normalizeQueryParam(tag);
        String normalizedFolder = workspaceQueryService.normalizeFolderQueryParam(folder);

        if (normalizedFolder == null) {
            Pageable pageable = PageRequest.of(safePage, safeSize);
            Page<Long> idPage = testCaseRepository.findWorkspaceCaseIdsByFilters(
                user.getTeamKey(),
                normalizedSearch,
                normalizedStatus,
                normalizedComponent,
                normalizedTag,
                null,
                pageable
            );

            List<TestCase> cases = workspaceQueryService.findCasesByOrderedIds(idPage.getContent());
            return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(idPage.getTotalElements()))
                .header("X-Total-Pages", String.valueOf(idPage.getTotalPages()))
                .header("X-Page", String.valueOf(idPage.getNumber()))
                .header("X-Page-Size", String.valueOf(idPage.getSize()))
                .header("X-Has-Next", String.valueOf(idPage.hasNext()))
                .body(cases);
        }

        Page<Long> allIdsPage = testCaseRepository.findWorkspaceCaseIdsByFilters(
            user.getTeamKey(),
            normalizedSearch,
            normalizedStatus,
            normalizedComponent,
            normalizedTag,
            null,
            Pageable.unpaged()
        );

        List<TestCase> matchingCases = workspaceQueryService.findCasesByOrderedIds(allIdsPage.getContent())
            .stream()
            .filter(testCase -> workspaceQueryService.folderMatchesFilter(testCase.getFolder(), normalizedFolder))
            .toList();

        int totalCount = matchingCases.size();
        int fromIndex = Math.min(safePage * safeSize, totalCount);
        int toIndex = Math.min(fromIndex + safeSize, totalCount);
        List<TestCase> pageCases = matchingCases.subList(fromIndex, toIndex);
        int totalPages = safeSize <= 0 ? 0 : (int) Math.ceil(totalCount / (double) safeSize);
        boolean hasNext = toIndex < totalCount;

        return ResponseEntity.ok()
            .header("X-Total-Count", String.valueOf(totalCount))
            .header("X-Total-Pages", String.valueOf(totalPages))
            .header("X-Page", String.valueOf(safePage))
            .header("X-Page-Size", String.valueOf(safeSize))
            .header("X-Has-Next", String.valueOf(hasNext))
            .body(pageCases);
    }

}
