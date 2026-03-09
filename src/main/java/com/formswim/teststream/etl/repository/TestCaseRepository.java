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

    @Query("select distinct testCase from TestCase testCase left join fetch testCase.steps")
    List<TestCase> findAllWithSteps();

    @Query("select distinct testCase from TestCase testCase left join fetch testCase.steps where testCase.workKey in :workKeys")
    List<TestCase> findAllWithStepsByWorkKeyIn(Collection<String> workKeys);

    Optional<TestCase> findByWorkKey(String workKey);
    boolean existsByWorkKey(String workKey);
    List<TestCase> findByFolder(String folder);
    List<TestCase> findByStatus(String status);
}