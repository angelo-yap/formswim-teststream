package com.formswim.teststream.shared.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {

    List<Tag> findByTeamKeyOrderByNameAsc(String teamKey);

    Optional<Tag> findByTeamKeyAndId(String teamKey, Long id);

    boolean existsByTeamKeyAndName(String teamKey, String name);
}
