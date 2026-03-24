package com.formswim.teststream.etl.controller;

import com.formswim.teststream.auth.model.AppUser;
import com.formswim.teststream.auth.repository.UserRepository;
import com.formswim.teststream.etl.dto.CreateTagRequest;
import com.formswim.teststream.etl.dto.RenameTagRequest;
import com.formswim.teststream.etl.dto.TagResponse;
import com.formswim.teststream.etl.model.Tag;
import com.formswim.teststream.etl.model.TestCase;
import com.formswim.teststream.etl.repository.TagRepository;
import com.formswim.teststream.etl.repository.TestCaseRepository;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping
public class TagController {

    private final TagRepository tagRepository;
    private final TestCaseRepository testCaseRepository;
    private final UserRepository userRepository;

    public TagController(TagRepository tagRepository,
                         TestCaseRepository testCaseRepository,
                         UserRepository userRepository) {
        this.tagRepository = tagRepository;
        this.testCaseRepository = testCaseRepository;
        this.userRepository = userRepository;
    }

    /** GET /api/tags — list filter tag options derived from testCaseType and components for the team */
    @GetMapping("/api/tags")
    @ResponseBody
    public ResponseEntity<List<String>> listTags(HttpSession session, Authentication authentication) {
        Optional<AppUser> currentUser = resolveCurrentUser(session, authentication);
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }
        AppUser user = currentUser.get();
        if (user.getTeamKey() == null || user.getTeamKey().isBlank()) {
            return ResponseEntity.status(403).build();
        }

        String teamKey = user.getTeamKey();
        Set<String> seen = new HashSet<>();
        Set<String> tags = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        testCaseRepository.findDistinctTestCaseTypeByTeamKey(teamKey)
            .forEach(v -> addDelimitedValues(tags, seen, v));
        testCaseRepository.findDistinctComponentsByTeamKey(teamKey)
            .forEach(v -> addDelimitedValues(tags, seen, v));

        return ResponseEntity.ok(new ArrayList<>(tags));
    }

    /** POST /api/tags — create a new custom tag for the team */
    @PostMapping("/api/tags")
    @ResponseBody
    @Transactional
    public ResponseEntity<TagResponse> createTag(@Valid @RequestBody CreateTagRequest request,
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

        String name = request.getName().trim();
        String normalized = Tag.normalize(name);
        if (normalized.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        // Return existing if name already taken (case-insensitive).
        Optional<Tag> existing = tagRepository.findByTeamKeyAndNormalizedName(user.getTeamKey(), normalized);
        if (existing.isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new TagResponse(existing.get()));
        }

        Tag tag = tagRepository.save(new Tag(user.getTeamKey(), name));
        return ResponseEntity.status(HttpStatus.CREATED).body(new TagResponse(tag));
    }

    /** PATCH /api/tags/{tagId} — rename a tag (updates all assigned test cases via the shared entity) */
    @PatchMapping("/api/tags/{tagId}")
    @ResponseBody
    @Transactional
    public ResponseEntity<TagResponse> renameTag(@PathVariable Long tagId,
                                                 @Valid @RequestBody RenameTagRequest request,
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

        Optional<Tag> tagOpt = tagRepository.findByIdAndTeamKey(tagId, user.getTeamKey());
        if (tagOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        String name = request.getName().trim();
        String normalized = Tag.normalize(name);
        if (normalized.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        // Check for name conflict with a different tag.
        Optional<Tag> conflict = tagRepository.findByTeamKeyAndNormalizedName(user.getTeamKey(), normalized);
        if (conflict.isPresent() && !conflict.get().getId().equals(tagId)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new TagResponse(conflict.get()));
        }

        Tag tag = tagOpt.get();
        tag.setName(name);
        tag = tagRepository.save(tag);
        return ResponseEntity.ok(new TagResponse(tag));
    }

    /** DELETE /api/tags/{tagId} — delete a tag and remove it from all test cases */
    @DeleteMapping("/api/tags/{tagId}")
    @ResponseBody
    @Transactional
    public ResponseEntity<Void> deleteTag(@PathVariable Long tagId,
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

        if (!tagRepository.existsByIdAndTeamKey(tagId, user.getTeamKey())) {
            return ResponseEntity.notFound().build();
        }

        tagRepository.deleteJoinEntriesByTagId(tagId);
        tagRepository.deleteById(tagId);
        return ResponseEntity.noContent().build();
    }

    /** POST /api/testcases/{workKey}/tags/{tagId} — assign a tag to a test case */
    @PostMapping("/api/testcases/{workKey}/tags/{tagId}")
    @ResponseBody
    @Transactional
    public ResponseEntity<List<TagResponse>> assignTag(@PathVariable String workKey,
                                                       @PathVariable Long tagId,
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

        Optional<TestCase> testCaseOpt = testCaseRepository.findByTeamKeyAndWorkKey(user.getTeamKey(), workKey);
        if (testCaseOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Optional<Tag> tagOpt = tagRepository.findByIdAndTeamKey(tagId, user.getTeamKey());
        if (tagOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        TestCase testCase = testCaseOpt.get();
        testCase.addTag(tagOpt.get());
        testCaseRepository.save(testCase);

        List<TagResponse> updatedTags = testCase.getTags().stream()
            .map(TagResponse::new)
            .sorted(Comparator.comparing(t -> t.getName().toLowerCase()))
            .collect(Collectors.toList());
        return ResponseEntity.ok(updatedTags);
    }

    /** DELETE /api/testcases/{workKey}/tags/{tagId} — remove a tag from a test case */
    @DeleteMapping("/api/testcases/{workKey}/tags/{tagId}")
    @ResponseBody
    @Transactional
    public ResponseEntity<List<TagResponse>> unassignTag(@PathVariable String workKey,
                                                         @PathVariable Long tagId,
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

        Optional<TestCase> testCaseOpt = testCaseRepository.findByTeamKeyAndWorkKey(user.getTeamKey(), workKey);
        if (testCaseOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        TestCase testCase = testCaseOpt.get();
        testCase.getTags().removeIf(t -> t.getId().equals(tagId));
        testCaseRepository.save(testCase);

        List<TagResponse> updatedTags = testCase.getTags().stream()
            .map(TagResponse::new)
            .sorted(Comparator.comparing(t -> t.getName().toLowerCase()))
            .collect(Collectors.toList());
        return ResponseEntity.ok(updatedTags);
    }

    private Optional<AppUser> resolveCurrentUser(HttpSession session, Authentication authentication) {
        String sessionEmail = (String) session.getAttribute("session_user");
        if (sessionEmail != null && !sessionEmail.isBlank()) {
            return userRepository.findByEmailIgnoreCase(sessionEmail.trim());
        }
        if (authentication != null && authentication.isAuthenticated()) {
            String name = authentication.getName();
            if (name != null && !name.isBlank() && !"anonymousUser".equals(name)) {
                return userRepository.findByEmailIgnoreCase(name);
            }
        }
        return Optional.empty();
    }
}
