package com.formswim.teststream.bulk.controllers;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.formswim.teststream.auth.model.AppUser;
import com.formswim.teststream.auth.service.CurrentUserService;
import com.formswim.teststream.bulk.dto.BulkEditRequest;
import com.formswim.teststream.bulk.dto.BulkEditResult;
import com.formswim.teststream.bulk.dto.BulkMoveRequest;
import com.formswim.teststream.bulk.dto.BulkMoveResult;
import com.formswim.teststream.bulk.service.TestCaseBulkEditService;
import com.formswim.teststream.bulk.service.TestCaseBulkMoveService;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@Controller
@RequestMapping
public class BulkMutationController {

    private static final String BULK_MOVE_INVALID_REQUEST = "INVALID_REQUEST";
    private static final String BULK_MOVE_CONFLICT = "CONFLICT";
    private static final String BULK_EDIT_INVALID_REQUEST = "INVALID_REQUEST";
    private static final String BULK_EDIT_CONFLICT = "CONFLICT";


    private final CurrentUserService currentUserService;
    private final TestCaseBulkEditService testCaseBulkEditService;
    private final TestCaseBulkMoveService testCaseBulkMoveService;


    BulkMutationController(CurrentUserService currentUserService,
                           TestCaseBulkEditService testCaseBulkEditService,
                           TestCaseBulkMoveService testCaseBulkMoveService) {
        this.currentUserService = currentUserService;
        this.testCaseBulkEditService = testCaseBulkEditService;
        this.testCaseBulkMoveService = testCaseBulkMoveService;
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

        try {
            BulkMoveResult result = testCaseBulkMoveService.bulkMoveByWorkKeys(user.getTeamKey(), request);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(buildBulkMoveErrorResult(request, BULK_MOVE_INVALID_REQUEST));
        } catch (DataIntegrityViolationException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(buildBulkMoveErrorResult(request, BULK_MOVE_CONFLICT));
        }
    }

    /**
     * PATCH /api/testcases/bulk-edit
     *
     * Runs team-scoped exact text replacement for selected work keys and fields.
     * Returns 400 for invalid payloads (blank find text, empty work keys, unsupported fields,
     * or over-limit batch sizes), and 401/403 for authentication or team access failures.
     */
    @PatchMapping("/api/testcases/bulk-edit")
    @ResponseBody
    public ResponseEntity<BulkEditResult> apiBulkEditTestCases(@Valid @RequestBody BulkEditRequest request,
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

        if (request.getWorkKeys().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        String normalizedFindText = normalizeTrimmed(request.getFindText());
        String normalizedStatusValue = normalizeTrimmed(request.getStatusValue());
        request.setFindText(normalizedFindText);
        request.setStatusValue(normalizedStatusValue);

        boolean hasTextOperation = !normalizedFindText.isBlank();
        boolean hasStatusOperation = !normalizedStatusValue.isBlank();
        if (!hasTextOperation && !hasStatusOperation) {
            return ResponseEntity.badRequest().build();
        }

        try {
            BulkEditResult result = testCaseBulkEditService.bulkEditByWorkKeys(user.getTeamKey(), request);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(buildBulkEditErrorResult(request, BULK_EDIT_INVALID_REQUEST));
        } catch (DataIntegrityViolationException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(buildBulkEditErrorResult(request, BULK_EDIT_CONFLICT));
        }
    }

    private String normalizeTrimmed(String value) {
        return value == null ? "" : value.trim();
    }
  
    private BulkMoveResult buildBulkMoveErrorResult(BulkMoveRequest request, String reason) {
        BulkMoveResult result = new BulkMoveResult();
        int requestedCount = request == null || request.getWorkKeys() == null ? 0 : request.getWorkKeys().size();
        result.setRequestedCount(requestedCount);
        result.setInvalidCount(requestedCount);
        result.getFailures().add(new BulkMoveResult.BulkMoveFailure(null, reason));
        return result;
    }

    private BulkEditResult buildBulkEditErrorResult(BulkEditRequest request, String reason) {
        BulkEditResult result = new BulkEditResult();
        int requestedCount = request == null || request.getWorkKeys() == null ? 0 : request.getWorkKeys().size();
        result.setRequestedCount(requestedCount);
        result.setInvalidCount(requestedCount);
        result.getFailures().add(new BulkEditResult.BulkEditFailure(null, reason));
        return result;
    }

}

