package com.formswim.teststream.workspace.dto;

import com.formswim.teststream.shared.domain.Tag;

public class TagResponse {

    private final Long id;
    private final String name;
    private final String color;
    private final long usageCount;

    public TagResponse(Long id, String name, String color, long usageCount) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.usageCount = usageCount;
    }

    public static TagResponse from(Tag tag) {
        return new TagResponse(tag.getId(), tag.getName(), tag.getColor(), 0);
    }

    public static TagResponse from(Tag tag, long usageCount) {
        return new TagResponse(tag.getId(), tag.getName(), tag.getColor(), usageCount);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getColor() {
        return color;
    }

    public long getUsageCount() {
        return usageCount;
    }
}
