package com.formswim.teststream.etl.repository;

import com.formswim.teststream.etl.model.UploadReviewSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UploadReviewSessionRepository extends JpaRepository<UploadReviewSession, String> {

    Optional<UploadReviewSession> findByIdAndTeamKey(String id, String teamKey);

    boolean existsByTeamKeyAndFileHashAndStatus(String teamKey, String fileHash, String status);
}
