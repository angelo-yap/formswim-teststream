package com.formswim.teststream.ingestion.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.formswim.teststream.ingestion.model.UploadHistory;

@Repository
public interface UploadHistoryRepository extends JpaRepository<UploadHistory, Long> {

    boolean existsByTeamKeyAndFileHash(String teamKey, String fileHash);
}
