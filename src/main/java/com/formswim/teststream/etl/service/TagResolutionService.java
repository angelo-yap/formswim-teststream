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
        Map<String, String> normalizedToDisplay = collectTokens(testCaseType, components);
        if (normalizedToDisplay.isEmpty()) {
            return List.of();
        }

        Map<String, Tag> existingByNormalized = new LinkedHashMap<>();
        for (Tag tag : tagRepository.findByTeamKeyOrderByNameAsc(teamKey)) {
            existingByNormalized.put(tag.getNormalizedName(), tag);
        }

        List<Tag> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : normalizedToDisplay.entrySet()) {
            Tag tag = existingByNormalized.get(entry.getKey());
            if (tag == null) {
                tag = tagRepository.save(new Tag(teamKey, entry.getValue()));
                existingByNormalized.put(tag.getNormalizedName(), tag);
            }
            result.add(tag);
        }
        return result;
    }

    /**
     * Batch variant for resolving implicit tags across many test cases in one operation.
     * Loads existing tags once and reuses them across all resolutions.
     */
    public BatchResolver batchResolverFor(String teamKey) {
        Map<String, Tag> existingByNormalized = new LinkedHashMap<>();
        for (Tag tag : tagRepository.findByTeamKeyOrderByNameAsc(teamKey)) {
            existingByNormalized.put(tag.getNormalizedName(), tag);
        }
        return new BatchResolver(teamKey, existingByNormalized);
    }

    public class BatchResolver {
        private final String teamKey;
        private final Map<String, Tag> cache;

        private BatchResolver(String teamKey, Map<String, Tag> cache) {
            this.teamKey = teamKey;
            this.cache = cache;
        }

        public List<Tag> resolve(String testCaseType, String components) {
            Map<String, String> tokens = collectTokens(testCaseType, components);
            if (tokens.isEmpty()) {
                return List.of();
            }
            List<Tag> result = new ArrayList<>();
            for (Map.Entry<String, String> entry : tokens.entrySet()) {
                Tag tag = cache.get(entry.getKey());
                if (tag == null) {
                    tag = tagRepository.save(new Tag(teamKey, entry.getValue()));
                    cache.put(tag.getNormalizedName(), tag);
                }
                result.add(tag);
            }
            return result;
        }
    }

    private Map<String, String> collectTokens(String testCaseType, String components) {
        Map<String, String> normalizedToDisplay = new LinkedHashMap<>();
        addToken(normalizedToDisplay, testCaseType);
        if (components != null && !components.isBlank()) {
            for (String part : components.split(",")) {
                addToken(normalizedToDisplay, part);
            }
        }
        return normalizedToDisplay;
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
}
