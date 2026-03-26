package com.formswim.teststream.etl.dto;

import com.formswim.teststream.etl.model.Tag;

public class TagResponse {

    private final Long id;
    private final String name;
    private final long count;

    public TagResponse(Tag tag) {
        this.id = tag.getId();
        this.name = tag.getName();
        this.count = 0;
    }

    public TagResponse(Tag tag, long count) {
        this.id = tag.getId();
        this.name = tag.getName();
        this.count = count;
    }

    public TagResponse(String name) {
        this.id = null;
        this.name = name;
        this.count = 0;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public long getCount() {
        return count;
    }
}
