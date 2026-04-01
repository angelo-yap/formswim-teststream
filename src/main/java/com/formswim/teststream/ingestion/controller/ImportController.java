package com.formswim.teststream.ingestion.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

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

import com.formswim.teststream.auth.model.AppUser;
import com.formswim.teststream.auth.service.CurrentUserService;
import com.formswim.teststream.ingestion.dto.EtlResultSummary;
import com.formswim.teststream.ingestion.dto.ReviewApplyResult;
import com.formswim.teststream.ingestion.dto.UploadReviewSessionView;
import com.formswim.teststream.ingestion.services.TestIngestionService;
import com.formswim.teststream.ingestion.services.UploadReviewService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping
public class ImportController {
  
    private final TestIngestionService testIngestionService;
    private final UploadReviewService uploadReviewService;
    private final CurrentUserService currentUserService;
    public ImportController(TestIngestionService testIngestionService,
                            UploadReviewService uploadReviewService,
                            CurrentUserService currentUserService) {
        this.testIngestionService = testIngestionService;
        this.uploadReviewService = uploadReviewService;
        this.currentUserService = currentUserService;
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


    
    private String encodeMessage(String message) {
        String value = message == null ? "" : message;
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
