package com.formswim.teststream.etl.service;

import com.formswim.teststream.etl.model.Tag;
import com.formswim.teststream.etl.repository.TagRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves implicit tag values (from testCaseType and components fields) into
 * proper Tag entities, creating them if they don't already exist for the team.
 *
 * Must be called from within an active transaction — does not manage its own.
 */
@Service
public class TagResolutionService {

    private final TagRepository tagRepository;

    public TagResolutionService(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    /**
     * Converts testCaseType (single value) and components (comma-separated) into
     * Tag entities, finding existing ones or creating new ones as needed.
     */
    public List<Tag> resolveTagsFromImplicitFields(String teamKey, String testCaseType, String components) {
        // Collect raw name tokens, preserving first-seen display casing per normalized key.
        Map<String, String> normalizedToDisplay = new LinkedHashMap<>();

        addToken(normalizedToDisplay, testCaseType);

        if (components != null && !components.isBlank()) {
            for (String part : components.split(",")) {
                addToken(normalizedToDisplay, part);
            }
        }

        List<Tag> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : normalizedToDisplay.entrySet()) {
            result.add(findOrCreate(teamKey, entry.getKey(), entry.getValue()));
        }
        return result;
    }

    private void addToken(Map<String, String> map, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        String display = raw.trim();
        String normalized = Tag.normalize(display);
        if (!normalized.isEmpty()) {
            map.putIfAbsent(normalized, display);
        }
    }

    private Tag findOrCreate(String teamKey, String normalizedName, String displayName) {
        return tagRepository.findByTeamKeyAndNormalizedName(teamKey, normalizedName)
            .orElseGet(() -> tagRepository.save(new Tag(teamKey, displayName)));
    }
}
