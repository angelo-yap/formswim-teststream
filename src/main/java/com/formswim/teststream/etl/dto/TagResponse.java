package com.formswim.teststream.etl.dto;

import com.formswim.teststream.etl.model.Tag;

public class TagResponse {

    private final Long id;
    private final String name;

    public TagResponse(Tag tag) {
        this.id = tag.getId();
        this.name = tag.getName();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
