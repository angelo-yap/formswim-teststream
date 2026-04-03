package com.formswim.teststream.workspace.dto;

import com.fasterxml.jackson.annotation.JsonSetter;

public class FolderUpdateRequest {

    private String name;
    private Long parentId;
    private boolean nameProvided;
    private boolean parentIdProvided;

    public String getName() {
        return name;
    }

    @JsonSetter("name")
    public void setName(String name) {
        this.name = name;
        this.nameProvided = true;
    }

    public Long getParentId() {
        return parentId;
    }

    @JsonSetter("parentId")
    public void setParentId(Long parentId) {
        this.parentId = parentId;
        this.parentIdProvided = true;
    }

    public boolean isNameProvided() {
        return nameProvided;
    }

    public boolean isParentIdProvided() {
        return parentIdProvided;
    }
}