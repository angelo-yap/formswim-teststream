package com.formswim.teststream.ingestion.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.formswim.teststream.ingestion.model.UploadReviewSession;

import java.util.Optional;

@Repository
public interface UploadReviewSessionRepository extends JpaRepository<UploadReviewSession, String> {

    Optional<UploadReviewSession> findByIdAndTeamKey(String id, String teamKey);

    boolean existsByTeamKeyAndFileHashAndStatus(String teamKey, String fileHash, String status);
}
