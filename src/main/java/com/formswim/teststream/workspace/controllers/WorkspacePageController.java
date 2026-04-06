package com.formswim.teststream.workspace.controllers;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.formswim.teststream.auth.model.AppUser;
import com.formswim.teststream.auth.service.CurrentUserService;
import com.formswim.teststream.shared.domain.TestCase;
import com.formswim.teststream.shared.domain.TestCaseRepository;
import com.formswim.teststream.workspace.services.WorkspaceQueryService;

import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping
public class WorkspacePageController {

    private final TestCaseRepository testCaseRepository;
    private final CurrentUserService currentUserService;
    private final WorkspaceQueryService workspaceQueryService;


    public WorkspacePageController(TestCaseRepository testCaseRepository,
                                  CurrentUserService currentUserService,
                                  WorkspaceQueryService workspaceQueryService
    ) {
        this.testCaseRepository = testCaseRepository;
        this.currentUserService = currentUserService;
        this.workspaceQueryService = workspaceQueryService;
    }


    @GetMapping("/workspace")
    public String workspace(HttpSession session,
                            Authentication authentication,
                            @RequestParam(required = false) String search,
                            @RequestParam(required = false) String status,
                            @RequestParam(required = false) String component,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "50") int size,
                            @RequestParam(required = false) String importError,
                            @RequestParam(required = false) String importSuccess,
                            Model model) {

        Optional<AppUser> currentUser = currentUserService.resolveCurrentUser(session, authentication);
        if (currentUser.isEmpty()) {
            return "redirect:/login?error=Please+log+in+first";
        }
        AppUser user = currentUser.get();
        if (user.getTeamKey() == null || user.getTeamKey().isBlank()) {
            return "redirect:/login?error=No+team+assigned";
        }

        String teamKey = user.getTeamKey();
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);
        Pageable pageable = PageRequest.of(safePage, safeSize);

        Page<Long> idPage = testCaseRepository.findWorkspaceCaseIdsByFilters(
            teamKey,
            workspaceQueryService.normalizeQueryParam(search),
            workspaceQueryService.normalizeQueryParam(status),
            workspaceQueryService.normalizeQueryParam(component),
            null,
            null,
            null,
            pageable
        );

        List<TestCase> cases = workspaceQueryService.findCasesByOrderedIds(idPage.getContent());

        model.addAttribute("userEmail", user.getEmail());
        model.addAttribute("teamKey", teamKey);
        model.addAttribute("testCases", cases);
        model.addAttribute("testCaseCount", idPage.getTotalElements());
        model.addAttribute("page", idPage.getNumber());
        model.addAttribute("pageSize", idPage.getSize());
        model.addAttribute("totalPages", idPage.getTotalPages());
        model.addAttribute("hasNext", idPage.hasNext());
        model.addAttribute("search", search != null ? search : "");
        model.addAttribute("filterStatus", status != null ? status : "");
        model.addAttribute("filterComponent", component != null ? component : "");
        model.addAttribute("importErrorMessage", importError);
        model.addAttribute("importSuccessMessage", importSuccess);

        return "workspace";
    }

    @GetMapping("/workspace/test-cases/{id}")
    public String testCaseDetails(@PathVariable String id,
                                  HttpSession session,
                                  Authentication authentication,
                                  Model model) {
        Optional<AppUser> currentUser = currentUserService.resolveCurrentUser(session, authentication);
        if (currentUser.isEmpty()) {
            return "redirect:/login?error=Please+log+in+first";
        }

        AppUser user = currentUser.get();
        if (user.getTeamKey() == null || user.getTeamKey().isBlank()) {
            return "redirect:/login?error=No+team+assigned";
        }

        String safeId = id == null ? "" : id.trim();
        if (safeId.isBlank()) {
            return "redirect:/workspace?importError=" + URLEncoder.encode("Test case id is required", StandardCharsets.UTF_8);
        }

        List<TestCase> matches = testCaseRepository.findAllWithStepsByTeamKeyAndWorkKeyIn(user.getTeamKey(), List.of(safeId));
        if (matches.isEmpty()) {
            return "redirect:/workspace?importError=" + URLEncoder.encode("Test case not found", StandardCharsets.UTF_8);
        }

        TestCase testCase = matches.get(0);

        model.addAttribute("detailsCaseId", testCase.getWorkKey());
        model.addAttribute("detailsPageTitle", "Test case details");
        model.addAttribute("detailsSummary", testCase.getSummary());
        model.addAttribute("detailsDescription", testCase.getDescription());
        model.addAttribute("detailsPrecondition", testCase.getPrecondition());
        model.addAttribute("detailsFolder", testCase.getFolder());
        model.addAttribute("detailsFolderSegments", workspaceQueryService.parseFolderSegments(testCase.getFolder()));
        model.addAttribute("detailsStatus", testCase.getStatus());
        model.addAttribute("detailsPriority", testCase.getPriority());
        model.addAttribute("detailsComponents", testCase.getComponents());
        model.addAttribute("detailsTestCaseType", testCase.getTestCaseType());
        model.addAttribute("detailsLabels", testCase.getLabels());
        model.addAttribute("detailsSprint", testCase.getSprint());
        model.addAttribute("detailsFixVersions", testCase.getFixVersions());
        model.addAttribute("detailsVersion", testCase.getVersion());
        model.addAttribute("detailsEstimatedTime", testCase.getEstimatedTime());
        model.addAttribute("detailsCreatedOn", testCase.getCreatedOn());
        model.addAttribute("detailsUpdatedOn", testCase.getUpdatedOn());
        model.addAttribute("detailsStoryLinkages", testCase.getStoryLinkages());
        model.addAttribute("detailsSteps", testCase.getSteps());

        return "test-case-details";
    }
  
}
