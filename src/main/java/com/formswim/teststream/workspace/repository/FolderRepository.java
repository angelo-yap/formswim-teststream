package com.formswim.teststream.workspace.repository;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.formswim.teststream.workspace.model.Folder;

@Repository
public interface FolderRepository extends JpaRepository<Folder, Long> {

    List<Folder> findByTeamKeyOrderByIdAsc(String teamKey);

    Optional<Folder> findByTeamKeyAndId(String teamKey, Long id);

    Optional<Folder> findByTeamKeyAndParent_IdAndNameIgnoreCase(String teamKey, Long parentId, String name);

        Optional<Folder> findByTeamKeyAndParentIsNullAndNameIgnoreCase(String teamKey, String name);

    boolean existsByTeamKeyAndParent_Id(String teamKey, Long parentId);
}