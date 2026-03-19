package com.formswim.teststream.etl.controller;

import com.formswim.teststream.auth.model.AppUser;
import com.formswim.teststream.auth.repository.UserRepository;
import com.formswim.teststream.etl.dto.EtlResultSummary;
import com.formswim.teststream.etl.dto.ReviewApplyResult;
import com.formswim.teststream.etl.dto.UploadReviewSessionView;
import com.formswim.teststream.etl.model.TestCase;
import com.formswim.teststream.etl.repository.TestCaseRepository;
import com.formswim.teststream.etl.service.ExcelExportService;
import com.formswim.teststream.etl.service.TestIngestionService;
import com.formswim.teststream.etl.service.UploadReviewService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping
public class TestCaseController {

    private final TestCaseRepository testCaseRepository;
    private final ExcelExportService excelExportService;
    private final TestIngestionService testIngestionService;
    private final UploadReviewService uploadReviewService;
    private final UserRepository userRepository;

    public TestCaseController(TestCaseRepository testCaseRepository,
                              ExcelExportService excelExportService,
                              TestIngestionService testIngestionService,
                              UploadReviewService uploadReviewService,
                              UserRepository userRepository) {
        this.testCaseRepository = testCaseRepository;
        this.excelExportService = excelExportService;
        this.testIngestionService = testIngestionService;
        this.uploadReviewService = uploadReviewService;
        this.userRepository = userRepository;
    }

    @GetMapping("/workspace")
    public String workspace(HttpSession session,
                            Authentication authentication,
                            @RequestParam(required = false) String search,
                            @RequestParam(required = false) String status,
                            @RequestParam(required = false) String component,
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
        List<TestCase> cases = testCaseRepository.findAllWithStepsByTeamKey(teamKey);

        if (search != null && !search.isBlank()) {
            String q = search.trim().toLowerCase();
            cases = cases.stream()
                .filter(tc -> (tc.getWorkKey() != null && tc.getWorkKey().toLowerCase().contains(q))
                    || (tc.getSummary() != null && tc.getSummary().toLowerCase().contains(q))
                    || (tc.getComponents() != null && tc.getComponents().toLowerCase().contains(q)))
                .collect(Collectors.toList());
        }
        if (status != null && !status.isBlank()) {
            cases = cases.stream()
                .filter(tc -> status.equalsIgnoreCase(tc.getStatus()))
                .collect(Collectors.toList());
        }
        if (component != null && !component.isBlank()) {
            cases = cases.stream()
                .filter(tc -> tc.getComponents() != null && tc.getComponents().toLowerCase().contains(component.toLowerCase()))
                .collect(Collectors.toList());
        }

        model.addAttribute("userEmail", user.getEmail());
        model.addAttribute("testCases", cases);
        model.addAttribute("testCaseCount", testCaseRepository.countByTeamKey(teamKey));
        model.addAttribute("search", search != null ? search : "");
        model.addAttribute("filterStatus", status != null ? status : "");
        model.addAttribute("filterComponent", component != null ? component : "");
        model.addAttribute("importErrorMessage", importError);
        model.addAttribute("importSuccessMessage", importSuccess);

        return "workspace";
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
                                                          Authentication authentication) {
        Optional<AppUser> currentUser = resolveCurrentUser(session, authentication);
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }
        AppUser user = currentUser.get();
        if (user.getTeamKey() == null || user.getTeamKey().isBlank()) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(testCaseRepository.findAllWithStepsByTeamKey(user.getTeamKey()));
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
}
