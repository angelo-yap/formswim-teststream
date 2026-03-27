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


//Shared Imports
import com.formswim.teststream.shared.domain.TestCase;
import com.formswim.teststream.shared.domain.TestCaseRepository;


import com.formswim.teststream.auth.model.AppUser;
import com.formswim.teststream.auth.repository.UserRepository;
import com.formswim.teststream.etl.dto.BulkEditRequest;
import com.formswim.teststream.etl.dto.BulkEditResult;
import com.formswim.teststream.etl.dto.BulkMoveRequest;
import com.formswim.teststream.etl.dto.BulkMoveResult;
import com.formswim.teststream.etl.dto.EtlResultSummary;
import com.formswim.teststream.etl.dto.ReviewApplyResult;
import com.formswim.teststream.etl.dto.UploadReviewSessionView;

import com.formswim.teststream.etl.service.ExcelExportService;
import com.formswim.teststream.etl.service.TestCaseBulkEditService;
import com.formswim.teststream.etl.service.TestCaseBulkMoveService;
import com.formswim.teststream.etl.service.TestIngestionService;
import com.formswim.teststream.etl.service.UploadReviewService;

import com.formswim.teststream.auth.service.CurrentUserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@Controller
@RequestMapping
public class TestCaseController {

    private final ExcelExportService excelExportService;
    private final TestCaseBulkEditService testCaseBulkEditService;
    private final TestCaseBulkMoveService testCaseBulkMoveService;
    private final TestIngestionService testIngestionService;
    private final UploadReviewService uploadReviewService;
    private final CurrentUserService currentUserService;
    private final boolean bulkEditEnabled;


    public TestCaseController(
                              ExcelExportService excelExportService,
                              TestCaseBulkEditService testCaseBulkEditService,
                              TestCaseBulkMoveService testCaseBulkMoveService,
                              TestIngestionService testIngestionService,
                              UploadReviewService uploadReviewService,
                                CurrentUserService currentUserService,
                              @Value("${teststream.bulk-edit.enabled:false}") boolean bulkEditEnabled) {
        this.excelExportService = excelExportService;
        this.testCaseBulkEditService = testCaseBulkEditService;
        this.testCaseBulkMoveService = testCaseBulkMoveService;
        this.testIngestionService = testIngestionService;
        this.uploadReviewService = uploadReviewService;
        this.currentUserService = currentUserService;
        this.bulkEditEnabled = bulkEditEnabled;
    }


    @PostMapping("/workspace/import")
    public String importFile(@RequestParam("file") MultipartFile file,
                             HttpSession session,
                             Authentication authentication) {

        Optional<AppUser> currentUser = currentUserService.resolveCurrentUser(session, authentication);
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
        Optional<AppUser> currentUser = currentUserService.resolveCurrentUser(session, authentication);
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
        Optional<AppUser> currentUser = currentUserService.resolveCurrentUser(session, authentication);
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
        Optional<AppUser> currentUser = currentUserService.resolveCurrentUser(session, authentication);
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


    @GetMapping("/workspace/export")
    @ResponseBody
    public ResponseEntity<ByteArrayResource> exportSelected(@RequestParam(required = false) List<String> workKeys,
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
        Optional<AppUser> currentUser = currentUserService.resolveCurrentUser(session, authentication);
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
        Optional<AppUser> currentUser = currentUserService.resolveCurrentUser(session, authentication);
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

        Optional<AppUser> currentUser = currentUserService.resolveCurrentUser(session, authentication);
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
     * Builds an export filename with a given prefix and current date.
     * @param prefix
     * @return
     */
    private String buildExportFilename(String prefix) {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        return prefix + "-" + date + ".xlsx";
    }


    private String encodeMessage(String message) {
        String value = message == null ? "" : message;
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }


}
