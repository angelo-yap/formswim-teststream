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
import com.formswim.teststream.etl.dto.EtlResultSummary;
import com.formswim.teststream.etl.dto.ReviewApplyResult;
import com.formswim.teststream.etl.dto.UploadReviewSessionView;

import com.formswim.teststream.etl.service.ExcelExportService;
import com.formswim.teststream.etl.service.TestIngestionService;
import com.formswim.teststream.etl.service.UploadReviewService;

import com.formswim.teststream.auth.service.CurrentUserService;
import com.formswim.teststream.bulk.dto.BulkEditRequest;
import com.formswim.teststream.bulk.dto.BulkEditResult;
import com.formswim.teststream.bulk.dto.BulkMoveRequest;
import com.formswim.teststream.bulk.dto.BulkMoveResult;
import com.formswim.teststream.bulk.service.TestCaseBulkEditService;
import com.formswim.teststream.bulk.service.TestCaseBulkMoveService;

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
