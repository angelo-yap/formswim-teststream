package com.formswim.teststream.workspace.services;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.formswim.teststream.shared.domain.Tag;
import com.formswim.teststream.shared.domain.TagRepository;
import com.formswim.teststream.shared.domain.TestCase;
import com.formswim.teststream.shared.domain.TestCaseRepository;
import com.formswim.teststream.workspace.dto.TagRequest;
import com.formswim.teststream.workspace.dto.TagResponse;

@Service
public class TagService {

    private static final java.util.regex.Pattern HEX_COLOR = java.util.regex.Pattern.compile("^#[0-9A-Fa-f]{6}$");

    private final TagRepository tagRepository;
    private final TestCaseRepository testCaseRepository;

    public TagService(TagRepository tagRepository, TestCaseRepository testCaseRepository) {
        this.tagRepository = tagRepository;
        this.testCaseRepository = testCaseRepository;
    }

    public List<TagResponse> listTags(String teamKey) {
        return tagRepository.findByTeamKeyOrderByNameAsc(teamKey)
                .stream()
                .map(TagResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public TagResponse createTag(String teamKey, TagRequest request) {
        String name = request.getName() == null ? "" : request.getName().trim();
        String color = request.getColor() == null ? "" : request.getColor().trim();

        if (name.isEmpty()) {
            throw new TagBadRequestException("Tag name is required.");
        }
        if (name.length() > 50) {
            throw new TagBadRequestException("Tag name must be 50 characters or fewer.");
        }
        if (!HEX_COLOR.matcher(color).matches()) {
            throw new TagBadRequestException("Tag color must be a valid hex color (e.g. #e11d48).");
        }
        if (tagRepository.existsByTeamKeyAndName(teamKey, name)) {
            throw new TagConflictException("A tag named \"" + name + "\" already exists.");
        }

        Tag tag = new Tag(name, color, teamKey);
        tag = tagRepository.save(tag);
        return TagResponse.from(tag);
    }

    @Transactional
    public void deleteTag(String teamKey, Long tagId) {
        Tag tag = tagRepository.findByTeamKeyAndId(teamKey, tagId)
                .orElseThrow(() -> new TagNotFoundException("Tag not found."));
        tagRepository.delete(tag);
    }

    @Transactional
    public List<TagResponse> setTagsForTestCase(String teamKey, String workKey, List<Long> tagIds) {
        TestCase testCase = testCaseRepository.findByTeamKeyAndWorkKey(teamKey, workKey)
                .orElseThrow(() -> new TagNotFoundException("Test case not found."));

        List<Tag> tags = tagIds == null
                ? List.of()
                : tagIds.stream()
                        .map(id -> tagRepository.findByTeamKeyAndId(teamKey, id)
                                .orElseThrow(() -> new TagNotFoundException("Tag " + id + " not found.")))
                        .collect(Collectors.toList());

        testCase.setCustomTags(tags);
        testCaseRepository.save(testCase);

        return tags.stream().map(TagResponse::from).collect(Collectors.toList());
    }
}
