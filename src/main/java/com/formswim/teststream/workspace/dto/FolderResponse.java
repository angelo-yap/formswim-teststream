package com.formswim.teststream.workspace.dto;

public record FolderResponse(Long id, String name, Long parentId, String path) {
}