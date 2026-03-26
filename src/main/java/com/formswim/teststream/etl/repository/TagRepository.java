package com.formswim.teststream.etl.repository;

import com.formswim.teststream.etl.model.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.query.Param;

@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {

    List<Tag> findByTeamKeyOrderByNameAsc(String teamKey);

    Optional<Tag> findByIdAndTeamKey(Long id, String teamKey);

    Optional<Tag> findByTeamKeyAndNormalizedName(String teamKey, String normalizedName);

    boolean existsByIdAndTeamKey(Long id, String teamKey);

    @Modifying
    @Query(value = "DELETE FROM test_case_tag WHERE tag_id = :tagId", nativeQuery = true)
    void deleteJoinEntriesByTagId(@Param("tagId") Long tagId);

    @Query(value = "SELECT tag_id, COUNT(*) FROM test_case_tag WHERE tag_id IN (SELECT id FROM tag WHERE team_key = :teamKey) GROUP BY tag_id", nativeQuery = true)
    List<Object[]> countUsageByTeamKey(@Param("teamKey") String teamKey);
}
