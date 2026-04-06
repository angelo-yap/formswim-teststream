package com.formswim.teststream.testcase.controllers;

import java.util.Map;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.formswim.teststream.auth.model.AppUser;
import com.formswim.teststream.auth.service.CurrentUserService;
import com.formswim.teststream.testcase.dto.TestCaseCreateRequest;
import com.formswim.teststream.testcase.dto.TestCaseCreateResponse;
import com.formswim.teststream.testcase.services.TestCaseBadRequestException;
import com.formswim.teststream.testcase.services.TestCaseConflictException;
import com.formswim.teststream.testcase.services.TestCaseNotFoundException;
import com.formswim.teststream.testcase.services.WorkspaceTestCaseMutationService;

import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping
public class TestCaseMutationController {

    private final CurrentUserService currentUserService;
    private final WorkspaceTestCaseMutationService workspaceTestCaseMutationService;

    public TestCaseMutationController(CurrentUserService currentUserService,
                                      WorkspaceTestCaseMutationService workspaceTestCaseMutationService) {
        this.currentUserService = currentUserService;
        this.workspaceTestCaseMutationService = workspaceTestCaseMutationService;
    }

    @PostMapping("/api/testcases")
    @ResponseBody
    public ResponseEntity<?> createTestCase(@RequestBody TestCaseCreateRequest request,
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

        try {
            TestCaseCreateResponse created = workspaceTestCaseMutationService.createBlankTestCase(
                user.getTeamKey(),
                user.getEmail(),
                request
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (TestCaseBadRequestException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        } catch (TestCaseConflictException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
        } catch (DataIntegrityViolationException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", "Testcase could not be created due to a conflicting update. Please retry."));
        } catch (TransactionSystemException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", "Testcase request failed validation."));
        }
    }

    @DeleteMapping("/api/testcases/{workKey}")
    @ResponseBody
    public ResponseEntity<?> deleteTestCase(@PathVariable String workKey,
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

        try {
            workspaceTestCaseMutationService.deleteTestCase(user.getTeamKey(), workKey);
            return ResponseEntity.noContent().build();
        } catch (TestCaseBadRequestException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        } catch (TestCaseNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
        } catch (DataIntegrityViolationException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", "Testcase could not be deleted because it is still referenced."));
        } catch (TransactionSystemException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", "Testcase delete request failed validation."));
        }
    }
}
