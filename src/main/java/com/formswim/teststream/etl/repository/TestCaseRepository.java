package com.formswim.teststream.etl.repository;

import com.formswim.teststream.etl.model.TestCase;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TestCaseRepository extends JpaRepository<TestCase, Long> {

    @Query("select distinct testCase from TestCase testCase left join fetch testCase.steps where testCase.teamKey = :teamKey")
    List<TestCase> findAllWithStepsByTeamKey(String teamKey);

    @Query("""
            select distinct testCase
            from TestCase testCase
            left join fetch testCase.steps
            where testCase.teamKey = :teamKey and testCase.workKey in :workKeys
            """)
    List<TestCase> findAllWithStepsByTeamKeyAndWorkKeyIn(String teamKey, Collection<String> workKeys);
    
    
    
    @Query("select distinct testCase.folder from TestCase testCase where testCase.teamKey = :teamKey")
    List<String> findDistinctFolderByTeamKey(String teamKey);
    
    List<TestCase> findByTeamKey(String teamKey);

    long countByTeamKey(String teamKey);

    long countByTeamKeyAndWorkKeyIn(String teamKey, Collection<String> workKeys);

    long countByWorkKeyIn(Collection<String> workKeys);
    Optional<TestCase> findByTeamKeyAndWorkKey(String teamKey, String workKey);
    boolean existsByTeamKeyAndWorkKey(String teamKey, String workKey);
    List<TestCase> findByTeamKeyAndFolder(String teamKey, String folder);
    List<TestCase> findByTeamKeyAndStatus(String teamKey, String status);
}