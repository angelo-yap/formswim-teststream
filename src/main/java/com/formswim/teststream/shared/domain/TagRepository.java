package com.formswim.teststream.shared.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {

    List<Tag> findByTeamKeyOrderByNameAsc(String teamKey);

    Optional<Tag> findByTeamKeyAndId(String teamKey, Long id);

    boolean existsByTeamKeyAndName(String teamKey, String name);

    @Query(value = "SELECT tag_id, COUNT(*) FROM test_case_custom_tags WHERE tag_id IN :tagIds GROUP BY tag_id", nativeQuery = true)
    List<Object[]> countUsageByTagIds(@Param("tagIds") List<Long> tagIds);

    @Modifying
    @Query(value = "DELETE FROM test_case_custom_tags WHERE tag_id = :tagId", nativeQuery = true)
    void deleteFromJoinTableByTagId(@Param("tagId") Long tagId);
}
