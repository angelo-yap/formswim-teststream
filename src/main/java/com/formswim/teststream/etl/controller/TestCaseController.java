package com.formswim.teststream.etl.controller;

import com.formswim.teststream.etl.dto.EtlResultSummary;
import com.formswim.teststream.etl.model.TestCase;
import com.formswim.teststream.etl.repository.TestCaseRepository;
import com.formswim.teststream.etl.service.TestIngestionService;

import jakarta.servlet.http.HttpSession;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@Controller
public class TestCaseController {

    private final TestIngestionService testIngestionService;
    private final TestCaseRepository testCaseRepository;

    public TestCaseController(TestIngestionService testIngestionService,
                              TestCaseRepository testCaseRepository) {
        this.testIngestionService = testIngestionService;
        this.testCaseRepository = testCaseRepository;
    }

    /**
     * GET /workspace
     * Loads all test cases from DB, applies optional search/filter params, renders workspace view.
     */
    @GetMapping("/workspace")
    public String workspace(HttpSession session,
                            Model model,
                            @RequestParam(required = false) String search,
                            @RequestParam(required = false) String status,
                            @RequestParam(required = false) String component) {

        String sessionEmail = (String) session.getAttribute("session_user");
        if (sessionEmail == null) {
            return "redirect:/login?error=Please log in first";
        }

        List<TestCase> cases = testCaseRepository.findAll();

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

        model.addAttribute("userEmail", sessionEmail);
        model.addAttribute("testCases", cases);
        model.addAttribute("testCaseCount", testCaseRepository.count());
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
                             HttpSession session) {

        String sessionEmail = (String) session.getAttribute("session_user");
        if (sessionEmail == null) {
            return "redirect:/login?error=Please log in first";
        }

        if (file == null || file.isEmpty()) {
            return "redirect:/workspace?importError=No+file+selected";
        }

        EtlResultSummary result = testIngestionService.ingestFile(file);

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
    public ResponseEntity<List<TestCase>> apiGetTestCases(HttpSession session) {
        if (session.getAttribute("session_user") == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(testCaseRepository.findAll());
    }

    /**
     * POST /api/upload
     * Accepts a multipart CSV or XLSX file, parses and saves to DB, returns JSON summary.
     * Used by the workspace import button via fetch().
     */
    @PostMapping("/api/upload")
    @ResponseBody
    public ResponseEntity<EtlResultSummary> apiUpload(@RequestParam("file") MultipartFile file,
                                                      HttpSession session) {
        if (session.getAttribute("session_user") == null) {
            return ResponseEntity.status(401).build();
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new EtlResultSummary(0, 0,
                            List.of("No file selected."), List.of()));
        }
        EtlResultSummary result = testIngestionService.ingestFile(file);
        return ResponseEntity.ok(result);
    }

}
