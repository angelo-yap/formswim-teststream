package com.formswim.teststream.etl.repository;

import com.formswim.teststream.etl.model.UploadHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UploadHistoryRepository extends JpaRepository<UploadHistory, Long> {

    boolean existsByTeamKeyAndFileHash(String teamKey, String fileHash);
}
