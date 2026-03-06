package com.formswim.teststream.etl.repository;

import com.formswim.teststream.etl.model.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TestCaseRepository extends JpaRepository<TestCase, Long> {

    Optional<TestCase> findByWorkKey(String workKey);
    boolean existsByWorkKey(String workKey);
    List<TestCase> findByFolder(String folder);
    List<TestCase> findByStatus(String status);


    
}