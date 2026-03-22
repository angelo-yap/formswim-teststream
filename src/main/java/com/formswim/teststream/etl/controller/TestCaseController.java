package com.formswim.teststream.etl.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.formswim.teststream.auth.model.AppUser;
import com.formswim.teststream.auth.repository.UserRepository;
import com.formswim.teststream.etl.dto.BulkEditRequest;
import com.formswim.teststream.etl.dto.BulkEditResult;
import com.formswim.teststream.etl.dto.BulkMoveRequest;
import com.formswim.teststream.etl.dto.BulkMoveResult;
import com.formswim.teststream.etl.dto.EtlResultSummary;
import com.formswim.teststream.etl.dto.ReviewApplyResult;
import com.formswim.teststream.etl.dto.UploadReviewSessionView;
import com.formswim.teststream.etl.model.TestCase;
import com.formswim.teststream.etl.repository.TestCaseRepository;
import com.formswim.teststream.etl.service.ExcelExportService;
import com.formswim.teststream.etl.service.TestCaseBulkEditService;
import com.formswim.teststream.etl.service.TestCaseBulkMoveService;
import com.formswim.teststream.etl.service.TestIngestionService;
import com.formswim.teststream.etl.service.UploadReviewService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@Controller
@RequestMapping
public class TestCaseController {

    private final TestCaseRepository testCaseRepository;
    private final ExcelExportService excelExportService;
    private final TestCaseBulkEditService testCaseBulkEditService;
    private final TestCaseBulkMoveService testCaseBulkMoveService;
    private final TestIngestionService testIngestionService;
    private final UploadReviewService uploadReviewService;
    private final UserRepository userRepository;
    private final boolean bulkEditEnabled;

    public TestCaseController(TestCaseRepository testCaseRepository,
                              ExcelExportService excelExportService,
                              TestCaseBulkEditService testCaseBulkEditService,
                              TestCaseBulkMoveService testCaseBulkMoveService,
                              TestIngestionService testIngestionService,
                              UploadReviewService uploadReviewService,
                              UserRepository userRepository,
                              @Value("${teststream.bulk-edit.enabled:false}") boolean bulkEditEnabled) {
        this.testCaseRepository = testCaseRepository;
        this.excelExportService = excelExportService;
        this.testCaseBulkEditService = testCaseBulkEditService;
        this.testCaseBulkMoveService = testCaseBulkMoveService;
        this.testIngestionService = testIngestionService;
        this.uploadReviewService = uploadReviewService;
        this.userRepository = userRepository;
        this.bulkEditEnabled = bulkEditEnabled;
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

        Optional<AppUser> currentUser = resolveCurrentUser(session, authentication);
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
            normalizeQueryParam(search),
            normalizeQueryParam(status),
            normalizeQueryParam(component),
            null,
            null,
            pageable
        );

        List<TestCase> cases = findCasesByOrderedIds(idPage.getContent());

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
        Optional<AppUser> currentUser = resolveCurrentUser(session, authentication);
        if (currentUser.isEmpty()) {
            return "redirect:/login?error=Please+log+in+first";
        }

        AppUser user = currentUser.get();
        if (user.getTeamKey() == null || user.getTeamKey().isBlank()) {
            return "redirect:/login?error=No+team+assigned";
        }

        String safeId = id == null ? "" : id.trim();
        if (safeId.isBlank()) {
            return "redirect:/workspace?importError=" + encodeMessage("Test case id is required");
        }

        List<TestCase> matches = testCaseRepository.findAllWithStepsByTeamKeyAndWorkKeyIn(user.getTeamKey(), List.of(safeId));
        if (matches.isEmpty()) {
            return "redirect:/workspace?importError=" + encodeMessage("Test case not found");
        }

        TestCase testCase = matches.get(0);

        model.addAttribute("detailsCaseId", testCase.getWorkKey());
        model.addAttribute("detailsPageTitle", "Test case details");
        model.addAttribute("detailsIsPlaceholder", false);
        model.addAttribute("detailsSummary", testCase.getSummary());
        model.addAttribute("detailsDescription", testCase.getDescription());
        model.addAttribute("detailsPrecondition", testCase.getPrecondition());
        model.addAttribute("detailsFolder", testCase.getFolder());
        model.addAttribute("detailsFolderSegments", parseFolderSegments(testCase.getFolder()));
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

    @PostMapping("/workspace/import")
    public String importFile(@RequestParam("file") MultipartFile file,
                             HttpSession session,
                             Authentication authentication) {

        Optional<AppUser> currentUser = resolveCurrentUser(session, authentication);
        if (currentUser.isEmpty()) {
            return "redirect:/login?error=Please+log+in+first";
        }
        AppUser user = currentUser.get();
        if (user.getTeamKey() == null || user.getTeamKey().isBlank()) {
            return "redirect:/workspace?importError=" + encodeMessage("User is missing team assignment");
        }
        if (file == null || file.isEmpty()) {
            return "redirect:/workspace?importError=" + encodeMessage("No file selected");
        }

        EtlResultSummary result = testIngestionService.ingestFile(file, user.getTeamKey());
        if (result.isReviewRequired() && result.getReviewUrl() != null) {
            return "redirect:" + result.getReviewUrl();
        }
        if (result.isExactDuplicateFile()) {
            return "redirect:/workspace?importError=" + encodeMessage(result.getMessage());
        }
        if (!result.getErrors().isEmpty()) {
            return "redirect:/workspace?importError=" + encodeMessage(result.getErrors().get(0));
        }
        return "redirect:/workspace?importSuccess=" + encodeMessage(result.getMessage());
    }

    @GetMapping("/workspace/import/review/{sessionId}")
    public String reviewUpload(@PathVariable String sessionId,
                               HttpSession session,
                               Authentication authentication,
                               Model model) {
        Optional<AppUser> currentUser = resolveCurrentUser(session, authentication);
        if (currentUser.isEmpty()) {
            return "redirect:/login?error=Please+log+in+first";
        }
        AppUser user = currentUser.get();
        if (user.getTeamKey() == null || user.getTeamKey().isBlank()) {
            return "redirect:/workspace?importError=" + encodeMessage("User is missing team assignment");
        }

        Optional<UploadReviewSessionView> reviewSession = uploadReviewService.getReviewSessionView(sessionId, user.getTeamKey());
        if (reviewSession.isEmpty()) {
            return "redirect:/workspace?importError=" + encodeMessage("Upload review session not found or already closed");
        }

        model.addAttribute("userEmail", user.getEmail());
        model.addAttribute("reviewSession", reviewSession.get());
        return "upload-review";
    }

    @PostMapping("/workspace/import/review/{sessionId}/apply")
    public String applyReview(@PathVariable String sessionId,
                              HttpServletRequest request,
                              HttpSession session,
                              Authentication authentication) {
        Optional<AppUser> currentUser = resolveCurrentUser(session, authentication);
        if (currentUser.isEmpty()) {
            return "redirect:/login?error=Please+log+in+first";
        }
        AppUser user = currentUser.get();
        if (user.getTeamKey() == null || user.getTeamKey().isBlank()) {
            return "redirect:/workspace?importError=" + encodeMessage("User is missing team assignment");
        }

        try {
            ReviewApplyResult result = uploadReviewService.applyReview(sessionId, user.getTeamKey(), request.getParameterMap());
            String message = "Upload applied. Imported " + result.getImportedNewCount() + " new, merged " + result.getMergedCount() + ", skipped " + result.getSkippedCount() + ".";
            return "redirect:/workspace?importSuccess=" + encodeMessage(message);
        } catch (IllegalArgumentException exception) {
            return "redirect:/workspace?importError=" + encodeMessage(exception.getMessage());
        }
    }

    @PostMapping("/workspace/import/review/{sessionId}/cancel")
    public String cancelReview(@PathVariable String sessionId,
                               HttpSession session,
                               Authentication authentication) {
        Optional<AppUser> currentUser = resolveCurrentUser(session, authentication);
        if (currentUser.isEmpty()) {
            return "redirect:/login?error=Please+log+in+first";
        }
        AppUser user = currentUser.get();
        if (user.getTeamKey() == null || user.getTeamKey().isBlank()) {
            return "redirect:/workspace?importError=" + encodeMessage("User is missing team assignment");
        }

        try {
            uploadReviewService.cancelReview(sessionId, user.getTeamKey());
            return "redirect:/workspace?importSuccess=" + encodeMessage("Upload review discarded.");
        } catch (IllegalArgumentException exception) {
            return "redirect:/workspace?importError=" + encodeMessage(exception.getMessage());
        }
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
        Optional<AppUser> currentUser = resolveCurrentUser(session, authentication);
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }
        AppUser user = currentUser.get();
        if (user.getTeamKey() == null || user.getTeamKey().isBlank()) {
            return ResponseEntity.status(403).build();
        }

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);
        Pageable pageable = PageRequest.of(safePage, safeSize);

        Page<Long> idPage = testCaseRepository.findWorkspaceCaseIdsByFilters(
            user.getTeamKey(),
            normalizeQueryParam(search),
            normalizeQueryParam(status),
            normalizeQueryParam(component),
            normalizeQueryParam(tag),
            normalizeQueryParam(folder),
            pageable
        );

        List<TestCase> cases = findCasesByOrderedIds(idPage.getContent());

        return ResponseEntity.ok()
            .header("X-Total-Count", String.valueOf(idPage.getTotalElements()))
            .header("X-Total-Pages", String.valueOf(idPage.getTotalPages()))
            .header("X-Page", String.valueOf(idPage.getNumber()))
            .header("X-Page-Size", String.valueOf(idPage.getSize()))
            .header("X-Has-Next", String.valueOf(idPage.hasNext()))
            .body(cases);
    }

    @GetMapping("/workspace/export")
    @ResponseBody
    public ResponseEntity<ByteArrayResource> exportSelected(@RequestParam(required = false) List<String> workKeys,
                                                            HttpSession session,
                                                            Authentication authentication) {
        Optional<AppUser> currentUser = resolveCurrentUser(session, authentication);
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        AppUser user = currentUser.get();
        if (user.getTeamKey() == null || user.getTeamKey().isBlank()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<TestCase> testCases;
        try {
            testCases = excelExportService.getSelectedTestCasesForExport(user.getTeamKey(), workKeys);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        String filename = buildExportFilename(workKeys == null || workKeys.isEmpty() ? "workspace-export" : "workspace-selected");
        return excelExportService.buildDownloadResponse(testCases, filename);
    }

    @PostMapping("/api/upload")
    @ResponseBody
    public ResponseEntity<EtlResultSummary> apiUpload(@RequestParam("file") MultipartFile file,
                                                      HttpSession session,
                                                      Authentication authentication) {
        Optional<AppUser> currentUser = resolveCurrentUser(session, authentication);
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }
        AppUser user = currentUser.get();
        if (user.getTeamKey() == null || user.getTeamKey().isBlank()) {
            return ResponseEntity.status(403).build();
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest()
                .body(new EtlResultSummary(0, 0, List.of("No file selected."), List.of()));
        }
        EtlResultSummary result = testIngestionService.ingestFile(file, user.getTeamKey());
        return ResponseEntity.ok(result);
    }
    /**
     * PATCH /api/testcases/bulk-move
     * Endpoint accepts a list of test case work keys and a target folder
     * and moves the specified test cases to the target folder, returning a summary of the results.
     * @param request
     * @param session
     * @param authentication
     * @return a summary of the bulk move results, including counts of moved, forbidden, not found, and any failures with reasons.
     */
    @PatchMapping("/api/testcases/bulk-move")
    @ResponseBody
    public ResponseEntity<BulkMoveResult> apiBulkMoveTestCases(@Valid @RequestBody BulkMoveRequest request,
                                                               HttpSession session,
                                                               Authentication authentication) {
        Optional<AppUser> currentUser = resolveCurrentUser(session, authentication);
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        AppUser user = currentUser.get();
        if (user.getTeamKey() == null || user.getTeamKey().isBlank()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String targetFolder = request.getTargetFolder() == null ? "" : request.getTargetFolder().trim();
        if (targetFolder.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        BulkMoveResult result = testCaseBulkMoveService.bulkMoveByWorkKeys(user.getTeamKey(), request);
        return ResponseEntity.ok(result);
    }

    /**
     * PATCH /api/testcases/bulk-edit
     *
     * <p>Runs team-scoped exact text replacement for selected work keys and fields.
     * Returns 400 for invalid payloads (blank find text, empty work keys, unsupported fields,
     * or over-limit batch sizes), 503 when disabled by rollout flag,
     * and 401/403 for authentication or team access failures.</p>
     */
    @PatchMapping("/api/testcases/bulk-edit")
    @ResponseBody
    public ResponseEntity<BulkEditResult> apiBulkEditTestCases(@Valid @RequestBody BulkEditRequest request,
                                                               HttpSession session,
                                                               Authentication authentication) {
        if (!bulkEditEnabled) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        Optional<AppUser> currentUser = resolveCurrentUser(session, authentication);
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        AppUser user = currentUser.get();
        if (user.getTeamKey() == null || user.getTeamKey().isBlank()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (request.getWorkKeys().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        String findText = request.getFindText() == null ? "" : request.getFindText().trim();
        if (findText.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        // Use the normalized token for mutation so validation and execution are consistent.
        request.setFindText(findText);

        try {
            BulkEditResult result = testCaseBulkEditService.bulkEditByWorkKeys(user.getTeamKey(), request);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().build();
        }
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
        Optional<AppUser> currentUser = resolveCurrentUser(session, authentication);
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
        Optional<AppUser> currentUser = resolveCurrentUser(session, authentication);
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }
        AppUser user = currentUser.get();
        if (user.getTeamKey() == null || user.getTeamKey().isBlank()) {
            return ResponseEntity.status(403).build();
        }

        Set<String> tags = new LinkedHashSet<>();
        testCaseRepository.findDistinctComponentsByTeamKey(user.getTeamKey()).forEach(value -> addDelimitedTags(tags, value));
        testCaseRepository.findDistinctTestCaseTypeByTeamKey(user.getTeamKey()).forEach(value -> addDelimitedTags(tags, value));

        List<String> sortedTags = tags.stream()
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .collect(Collectors.toList());
        return ResponseEntity.ok(sortedTags);
    }

    @GetMapping("/api/components")
    @ResponseBody
    public ResponseEntity<List<String>> getComponentsByTeamKey(HttpSession session, Authentication authentication) {
        Optional<AppUser> currentUser = resolveCurrentUser(session, authentication);
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }
        AppUser user = currentUser.get();
        if (user.getTeamKey() == null || user.getTeamKey().isBlank()) {
            return ResponseEntity.status(403).build();
        }

        Set<String> components = new LinkedHashSet<>();
        testCaseRepository.findDistinctComponentsByTeamKey(user.getTeamKey()).forEach(value -> addDelimitedTags(components, value));

        List<String> sortedComponents = components.stream()
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .collect(Collectors.toList());
        return ResponseEntity.ok(sortedComponents);
    }

    @GetMapping("/api/statuses")
    @ResponseBody
    public ResponseEntity<List<String>> getStatusesByTeamKey(HttpSession session, Authentication authentication) {
        Optional<AppUser> currentUser = resolveCurrentUser(session, authentication);
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

    /**
     * Builds an export filename with a given prefix and current date.
     * @param prefix
     * @return
     */
    private String buildExportFilename(String prefix) {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        return prefix + "-" + date + ".xlsx";
    }

    private Optional<AppUser> resolveCurrentUser(HttpSession session, Authentication authentication) {
        String sessionEmail = (String) session.getAttribute("session_user");
        if (sessionEmail != null && !sessionEmail.isBlank()) {
            return userRepository.findByEmailIgnoreCase(sessionEmail.trim());
        }

        String principalName = resolveAuthenticatedPrincipalName(authentication);
        if (principalName == null) {
            return Optional.empty();
        }
        return userRepository.findByEmailIgnoreCase(principalName);
    }

    private String resolveAuthenticatedPrincipalName(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            String principalName = authentication.getName();
            if (principalName != null && !principalName.isBlank() && !"anonymousUser".equals(principalName)) {
                return principalName;
            }
        }
        return null;
    }

    private String encodeMessage(String message) {
        String value = message == null ? "" : message;
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String normalizeQueryParam(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private List<TestCase> findCasesByOrderedIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        List<TestCase> cases = testCaseRepository.findAllWithStepsByIdIn(ids);
        Map<Long, Integer> positionById = new HashMap<>();
        for (int index = 0; index < ids.size(); index++) {
            positionById.put(ids.get(index), index);
        }
        cases.sort(Comparator.comparingInt(testCase -> positionById.getOrDefault(testCase.getId(), Integer.MAX_VALUE)));
        return cases;
    }

    private void addDelimitedTags(Set<String> tags, String rawValue) {
        if (rawValue == null) {
            return;
        }
        Arrays.stream(rawValue.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .forEach(tags::add);
    }

    private List<String> parseFolderSegments(String folder) {
        if (folder == null || folder.isBlank()) {
            return List.of();
        }

        return Arrays.stream(folder.split("/"))
            .map(String::trim)
            .filter(segment -> !segment.isBlank())
            .collect(Collectors.toList());
    }
}
