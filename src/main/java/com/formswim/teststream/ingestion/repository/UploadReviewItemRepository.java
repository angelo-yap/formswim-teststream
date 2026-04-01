package com.formswim.teststream.ingestion.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.formswim.teststream.ingestion.model.UploadReviewItem;

@Repository
public interface UploadReviewItemRepository extends JpaRepository<UploadReviewItem, Long> {
}
