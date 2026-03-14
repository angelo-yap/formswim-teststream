package com.formswim.teststream.etl.controller;

import com.formswim.teststream.auth.model.AppUser;
import com.formswim.teststream.auth.repository.UserRepository;
import com.formswim.teststream.etl.dto.EtlResultSummary;
import com.formswim.teststream.etl.model.TestCase;
import com.formswim.teststream.etl.repository.TestCaseRepository;
import com.formswim.teststream.etl.service.TestIngestionService;

import jakarta.servlet.http.HttpSession;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.formswim.teststream.etl.service.ExcelExportService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
public class TestCaseController {

    private final TestIngestionService testIngestionService;
    private final TestCaseRepository testCaseRepository;
    private final ExcelExportService excelExportService;
    private final UserRepository userRepository;

    public TestCaseController(TestIngestionService testIngestionService,
                              TestCaseRepository testCaseRepository,
                              ExcelExportService excelExportService,
                              UserRepository userRepository) {
        this.testIngestionService = testIngestionService;
        this.testCaseRepository = testCaseRepository;
        this.excelExportService = excelExportService;
        this.userRepository = userRepository;
    }

    /**
     * GET /workspace
     * Loads all test cases from DB, applies optional search/filter params, renders workspace view.
     */
    @GetMapping("/workspace")
    public String workspace(HttpSession session,
                            Authentication authentication,
                            Model model,
                            @RequestParam(required = false) String search,
                            @RequestParam(required = false) String status,
                            @RequestParam(required = false) String component) {

        Optional<AppUser> currentUser = resolveCurrentUser(session, authentication);
        if (currentUser.isEmpty()) {
            return "redirect:/login?error=Please log in first";
        }
        AppUser user = currentUser.get();
        if (user.getTeamKey() == null || user.getTeamKey().isBlank()) {
            return "redirect:/login?error=User+is+missing+team+assignment";
        }

        String teamKey = user.getTeamKey();
        List<TestCase> cases = testCaseRepository.findByTeamKey(teamKey);

        if (search != null && !search.isBlank()) {
            String q = search.trim().toLowerCase();
            cases = cases.stream()
                .filter(tc -> (tc.getWorkKey()   != null && tc.getWorkKey().toLowerCase().contains(q))
                           || (tc.getSummary()   != null && tc.getSummary().toLowerCase().contains(q))
                           || (tc.getComponents()!= null && tc.getComponents().toLowerCase().contains(q)))
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

        return "workspace";
    }

    /**
     * POST /workspace/import
     * Accepts a multipart .xlsx file, parses and saves to DB, then redirects back to /workspace.
     */
    @PostMapping("/workspace/import")
    public String importFile(@RequestParam("file") MultipartFile file,
                             HttpSession session,
                             Authentication authentication) {

        Optional<AppUser> currentUser = resolveCurrentUser(session, authentication);
        if (currentUser.isEmpty()) {
            return "redirect:/login?error=Please log in first";
        }
        AppUser user = currentUser.get();
        if (user.getTeamKey() == null || user.getTeamKey().isBlank()) {
            return "redirect:/workspace?importError=User+is+missing+team+assignment";
        }

        if (file == null || file.isEmpty()) {
            return "redirect:/workspace?importError=No+file+selected";
        }

        EtlResultSummary result = testIngestionService.ingestFile(file, user.getTeamKey());

        if (!result.getErrors().isEmpty()) {
            String msg = result.getErrors().get(0).replace(" ", "+");
            return "redirect:/workspace?importError=" + msg;
        }

        return "redirect:/workspace?importSuccess=" + result.getTestCasesParsed();
    }

    /**
     * GET /api/testcases
     * Returns all test cases as JSON for the front-end to load on page init.
     */
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

    /**
     * POST /api/upload
     * Accepts a multipart CSV or XLSX file, parses and saves to DB, returns JSON summary.
     * Used by the workspace import button via fetch().
     */
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
                    .body(new EtlResultSummary(0, 0,
                            List.of("No file selected."), List.of()));
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

}
