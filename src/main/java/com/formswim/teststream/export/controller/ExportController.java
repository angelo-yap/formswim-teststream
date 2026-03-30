package com.formswim.teststream.export.controller;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.formswim.teststream.auth.model.AppUser;
import com.formswim.teststream.auth.service.CurrentUserService;
import com.formswim.teststream.export.services.ExcelExportService;
import com.formswim.teststream.shared.domain.TestCase;

import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping
public class ExportController {
  
    private final ExcelExportService excelExportService;
    private final CurrentUserService currentUserService;


    public ExportController(
                              ExcelExportService excelExportService,
                                CurrentUserService currentUserService) {
        this.excelExportService = excelExportService;
        this.currentUserService = currentUserService;
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



    /**
     * Builds an export filename with a given prefix and current date.
     * @param prefix
     * @return
     */
    private String buildExportFilename(String prefix) {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        return prefix + "-" + date + ".xlsx";
    }



}
