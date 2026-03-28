package com.formswim.teststream.workspace.services;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.formswim.teststream.shared.domain.TestCase;
import com.formswim.teststream.shared.domain.TestCaseRepository;

@Service
public class WorkspaceQueryService {
    private final TestCaseRepository testCaseRepository;

    public WorkspaceQueryService(TestCaseRepository testCaseRepository) {
        this.testCaseRepository = testCaseRepository;
    }

      public String normalizeQueryParam(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
    
    public List<TestCase> findCasesByOrderedIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        List<TestCase> cases = testCaseRepository.findAllWithStepsByIdIn(ids);
        Map<Long, Integer> positionById = new HashMap<>();
        for (int index = 0; index < ids.size(); index++) {
            positionById.put(ids.get(index), index);
        }
        cases.sort(Comparator.comparingInt(testCase -> positionById.getOrDefault(testCase.getId(), Integer.MAX_VALUE)));
        return cases;
    }
    

    public String normalizeFolderQueryParam(String value) {
        String normalized = normalizeFolderPath(value);
        if (normalized.isEmpty()) {
            return null;
        }

        return normalized;
    }

    private String normalizeFolderPath(String value) {
        if (value == null) {
            return "";
        }

        String withSlashSeparators = value.trim().replace('\\', '/').replaceAll("/+", "/");
        String trimmedBounds = withSlashSeparators.replaceAll("^/+", "").replaceAll("/+$", "").trim();
        return trimmedBounds;
    }

    public boolean folderMatchesFilter(String folderValue, String normalizedFilter) {
        String normalizedFolder = normalizeFolderPath(folderValue);
        if (normalizedFolder.isEmpty() || normalizedFilter == null || normalizedFilter.isEmpty()) {
            return false;
        }

        String folderLower = normalizedFolder.toLowerCase();
        String filterLower = normalizedFilter.toLowerCase();
        return folderLower.equals(filterLower)
            || folderLower.startsWith(filterLower + "/");
    }


    public void addDelimitedTags(Set<String> tags, String rawValue) {
        if (rawValue == null) {
            return;
        }
        Arrays.stream(rawValue.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .forEach(tags::add);
    }

    public List<String> parseFolderSegments(String folder) {
        if (folder == null || folder.isBlank()) {
            return List.of();
        }

        return Arrays.stream(folder.split("/"))
            .map(String::trim)
            .filter(segment -> !segment.isBlank())
            .collect(Collectors.toList());
    }
}
