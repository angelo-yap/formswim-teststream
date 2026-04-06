package com.formswim.teststream.workspace.dto;

import com.formswim.teststream.shared.domain.Tag;

public class TagResponse {

    private final Long id;
    private final String name;
    private final String color;

    public TagResponse(Long id, String name, String color) {
        this.id = id;
        this.name = name;
        this.color = color;
    }

    public static TagResponse from(Tag tag) {
        return new TagResponse(tag.getId(), tag.getName(), tag.getColor());
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
}
